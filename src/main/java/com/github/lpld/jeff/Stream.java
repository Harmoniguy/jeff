package com.github.lpld.jeff;

import com.github.lpld.jeff.LList.LNil;
import com.github.lpld.jeff.data.Or;
import com.github.lpld.jeff.data.Pr;
import com.github.lpld.jeff.data.Unit;
import com.github.lpld.jeff.functions.Fn;
import com.github.lpld.jeff.functions.Fn2;
import com.github.lpld.jeff.functions.Xn;
import com.github.lpld.jeff.functions.Xn0;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import static com.github.lpld.jeff.IO.IO;
import static com.github.lpld.jeff.IO.Pure;
import static com.github.lpld.jeff.IO.Suspend;
import static com.github.lpld.jeff.data.Pr.Pr;

/**
 * @author leopold
 * @since 10/10/18
 */
public abstract class Stream<T> {

  /**
   * Create an empty stream
   */
  public static <T> Stream<T> Nil() {
    return Nil.instance();
  }

  /**
   * Create a stream by combining an effectful head and a tail.
   */
  public static <T> Stream<T> SCons(IO<T> head, Stream<T> tail) {
    return new Cons<>(head, tail);
  }

  /**
   * Create a stream by combining a head and a tail.
   */
  public static <T> Stream<T> Cons(T head, Stream<T> tail) {
    return SCons(Pure(head), tail);
  }

  /**
   * Defer the evaluation of the stream.
   */
  public static <T> Stream<T> Defer(IO<Stream<T>> streamEval) {
    return new Defer<>(streamEval);
  }

  /**
   * Shortcut for {@code Stream.Defer(IO.Delay(streamEval))}.
   */
  public static <T> Stream<T> Lazy(Xn0<Stream<T>> streamEval) {
    return Defer(IO(streamEval));
  }

  /**
   * Create a stream that consists of given elements.
   */
  @SafeVarargs
  public static <T> Stream<T> of(T... elements) {
    return fromList(Arrays.asList(elements), IO::Pure);
  }

  /**
   * Shortcut for {@code Stream.of(elements)}
   */
  @SafeVarargs
  public static <T> Stream<T> Stream(T... elements) {
    return of(elements);
  }

  /**
   * Create a stream that consists of given elements that are wrapped in IO effect and are
   * possibly not evaluated yet.
   */
  @SafeVarargs
  public static <T> Stream<T> eval(IO<T>... ios) {
    return fromList(Arrays.asList(ios), Function.identity());
  }

  /**
   * Create a stream of elements of a given iterable.
   */
  public static <T> Stream<T> ofAll(Iterable<T> elems) {
    return elems instanceof List ? fromList(((List<T>) elems), IO::Pure)
                                 : fromIterable(elems, IO::Pure);
  }

  public static Stream<Unit> awakeEvery(ScheduledExecutorService scheduler, long millis) {
    return Stream.eval(IO.sleep(scheduler, millis)).repeat();
  }

  /**
   * General stream building function.
   *
   * It receives a seed value {@code z} and a function {@code f} that continuously evaluates
   * next stream value along with a new state {@code z}. Stream generation is performed by
   * applying the function {@code f} to the state from previous step until it returns
   * {@code Optional.empty()}.
   *
   * The resulting stream is lazy, so this function can be used to produce infinite streams. For
   * instance, this will generate endless stream containing all natural numbers starting from 0:
   *
   * {@code Stream.unfold(0, i -> Optional.of(Pr(i, i + 1)) }
   */
  public static <T, S> Stream<T> unfold(S z, Xn<S, Optional<Pr<T, S>>> f) {
    // we want to defer the first step:
    return Lazy(() -> unfoldEager(z, f));
  }

  public static <T> Stream<T> iterate(Xn0<Optional<T>> more) {
    return unfold(Unit.unit, u -> more.ap().map(next -> Pr(next, Unit.unit)));
  }

  private static <T, M> Stream<T> fromList(List<M> list, Function<M, IO<T>> f) {
    Stream<T> s = Nil();
    for (int i = list.size() - 1; i >= 0; i--) {
      s = SCons(f.apply(list.get(i)), s);
    }
    return s;
  }

  private static <T, M> Stream<T> fromIterable(Iterable<M> iterable, Function<M, IO<T>> f) {
    return fromIterator(iterable.iterator(), f).run();
  }

  private static <T, M> IO<Stream<T>> fromIterator(Iterator<M> iterator, Function<M, IO<T>> f) {
    return Suspend(() -> {

      if (!iterator.hasNext()) {
        return Pure(Nil());
      }
      final M next = iterator.next();
      return fromIterator(iterator, f).map(s -> SCons(f.apply(next), s));
    });
  }

  // Unfold that eagerly evaluates the first step:
  private static <T, S> Stream<T> unfoldEager(S z, Xn<S, Optional<Pr<T, S>>> f) throws Throwable {
    return f.ap(z)
        .map(p -> SCons(Pure(p._1), Lazy((() -> unfoldEager(p._2, f)))))
        .orElseGet(Stream::Nil);
  }

  public abstract <R> IO<R> foldRight(IO<R> z, Fn2<T, IO<R>, IO<R>> f);

  public <R> IO<R> foldRight(R z, Fn2<T, R, R> f) {
    return foldRight(Pure(z), (t, ior) -> ior.map(r -> f.ap(t, r)));
  }

  /**
   * Similar to foldRight, but does not evaluate elements
   */
  public abstract <R> IO<R> collectRight(IO<R> z, Fn2<IO<T>, IO<R>, IO<R>> f);

  public abstract <R> IO<R> foldLeft(R z, Fn2<R, T, R> f);

  /**
   * Similar to foldLeft, but does not evaluate elements
   */
  public abstract <R> IO<R> collectLeft(IO<R> z, Fn2<IO<R>, IO<T>, IO<R>> f);

  public Stream<T> append(Stream<T> other) {
    if (other instanceof Nil) {
      return this;
    }

    if (this instanceof Nil) {
      return other;
    }

    return Defer(collectRight(Pure(other), (elem, acc) -> Pure(SCons(elem, Defer(acc)))));
  }

  public abstract Stream<T> take(int n);

  public abstract Stream<T> drop(int n);

  public Stream<T> head() {
    return take(1);
  }

  public Stream<T> tail() {
    return drop(1);
  }

  public abstract Stream<T> takeWhile(Fn<T, Boolean> p, boolean includeFailure);

  public Stream<T> takeWhile(Fn<T, Boolean> p) {
    return takeWhile(p, false);
  }

  public abstract Stream<T> dropWhile(Fn<T, Boolean> p);

  public abstract <U> Stream<U> flatMap(Fn<T, Stream<U>> f);

  public abstract <U> Stream<U> map(Fn<T, U> f);

  public abstract <U> Stream<U> mapEval(Fn<T, IO<U>> f);

  public <U> Stream<U> chain(IO<U> f) {
    return mapEval(any -> f);
  }

  public abstract Stream<T> filter(Fn<T, Boolean> p);

  public IO<Boolean> exists(Fn<T, Boolean> p) {
    return foldRight(Pure(false), (elem, searchMore) -> p.ap(elem) ? Pure(true) : searchMore);
  }

  public IO<Boolean> forall(Fn<T, Boolean> p) {
    return foldRight(Pure(true), (elem, searchMore) -> p.ap(elem) ? searchMore : Pure(false));
  }

  public Stream<T> reverse() {
    return Defer(collectLeft(Pure(Nil()), (acc, elem) -> Pure(SCons(elem, Defer(acc)))));
  }

  public Stream<T> repeat() {
    return append(Lazy(this::repeat));
  }

  /**
   * Return head but don't evaluate it. This method can help determine if
   * the stream actually has a head (is not empty) without really evaluating head's value:
   * {@code
   * final Stream<T> stream = ...
   * final IO<Boolean> isNotEmpty = stream.lazyHead().map(Optional::isPresent)
   * }.
   *
   * This method can be implemented in terms of {@code collectRight}:
   * {@code
   * collectRight(Pure(Optional.empty()), (head, ignore) -> Pure(Optional.of(head)));
   * }
   */
  public abstract IO<Optional<IO<T>>> lazyHead();

  public abstract IO<Optional<T>> headOption();

  /**
   * Run all the effects in the stream!
   */
  public IO<Unit> drain() {
    return foldLeft(Unit.unit, (u, any) -> u);
  }

  public static <T, U, V> Stream<V> zipWith(Executor executor,
                                            Stream<T> stream1, Stream<U> stream2,
                                            Fn2<T, U, V> combine) {

    return Defer(
        IO.both(executor, stream1.lazyHead(), stream2.lazyHead())
            .map(res -> res._1.isPresent() && res._2.isPresent()
                        ? Defer(IO.both(executor, res._1.get(), res._2.get())
                                    .map(pr -> Cons(combine.ap(pr._1, pr._2),
                                                    Stream.zipWith(executor,
                                                                   stream1.tail(), stream2.tail(),
                                                                   combine))
                                    ))
                        : Nil()
            )
    );
  }

  public static <T, U> Stream<Pr<T, U>> zip(Executor executor,
                                            Stream<T> stream1, Stream<U> stream2) {
    return zipWith(executor, stream1, stream2, Pr::of);
  }

  public static <T> Stream<T> merge(Executor executor, Stream<T> stream1, Stream<T> stream2) {

    if (stream1 instanceof Nil) {
      return stream2;
    }

    if (stream2 instanceof Nil) {
      return stream1;
    }

    return Defer(
        IO.seq(executor, stream1.headOption(), stream2.headOption())
            .map(res -> {

              final Pr<Optional<T>, IO<Optional<T>>> hSeq = Or.flatten(res);
              final Pr<Stream<T>, Stream<T>> sSeq =
                  res.fold(l -> Pr(stream1, stream2), r -> Pr(stream2, stream1));

              // joining 2nd head and 2nd tail.
              // this is almost equivalent to sSeq._2, but the head is already being evaluated
              // at the moment and we don't want to run its effect twice
              final Stream<T> str2 =
                  Defer(hSeq._2.map(
                      h2Opt -> h2Opt.map(h2Val -> Cons(h2Val, sSeq._2.tail())).orElse(Nil())));

              // if 1st head is Optional.empty, then we just return 2nd stream:
              // if 1st head is not empty, we join it with the rest:
              return hSeq._1
                  .map(h1Val -> Cons(h1Val, merge(executor, sSeq._1.tail(), str2)))
                  .orElse(str2);
            })
    );
  }

  IO<LList<T>> toLList() {
    return foldRight(LNil.instance(), (el, l) -> l.prepend(el));
  }

  <U> Stream<U> lazyTransform(Function<Stream<T>, Stream<U>> conv) {
    if (this instanceof Cons) {
      return Lazy(() -> conv.apply(this));
    }

    return conv.apply(this);
  }
}

@RequiredArgsConstructor
class Cons<T> extends Stream<T> {

  final IO<T> head;
  final Stream<T> tail;

  @Override
  public <R> IO<R> foldRight(IO<R> z, Fn2<T, IO<R>, IO<R>> f) {
    return head.flatMap(h -> f.ap(h, tail.foldRight(z, f)));
  }

  @Override
  public <R> IO<R> collectRight(IO<R> z, Fn2<IO<T>, IO<R>, IO<R>> f) {
    return Suspend(() -> f.ap(head, tail.collectRight(z, f)));
  }

  @Override
  public <R> IO<R> foldLeft(R z, Fn2<R, T, R> f) {
    return head.flatMap(h -> tail.foldLeft(f.ap(z, h), f));
  }

  @Override
  public <R> IO<R> collectLeft(IO<R> z, Fn2<IO<R>, IO<T>, IO<R>> f) {
    return tail.collectLeft(Suspend(() -> f.ap(z, head)), f);
  }

  @Override
  public Stream<T> filter(Fn<T, Boolean> p) {
    return Defer(head.map(h -> p.ap(h) ? Cons(h, tail.filter(p)) : tail.filter(p)));
  }

  @Override
  public Stream<T> take(int n) {
    return n == 0 ? Nil() : SCons(head, tail.lazyTransform(s -> s.take(n - 1)));
  }

  @Override
  public Stream<T> drop(int n) {
    return n == 0 ? this : tail.lazyTransform(s -> s.drop(n - 1));
  }

  @Override
  public Stream<T> takeWhile(Fn<T, Boolean> p, boolean includeFailure) {
    return Defer(head.map(h -> p.ap(h) ? Cons(h, tail.takeWhile(p))
                                       : includeFailure ? Cons(h, Nil())
                                                        : Nil()
    ));
  }

  @Override
  public Stream<T> dropWhile(Fn<T, Boolean> p) {
    return Defer(head.map(h -> p.ap(h) ? tail.dropWhile(p) : this));
  }

  @Override
  public <U> Stream<U> flatMap(Fn<T, Stream<U>> f) {
    return Defer(head.map(h -> f.ap(h).append(tail.flatMap(f))));
  }

  @Override
  public <U> Stream<U> map(Fn<T, U> f) {
    return SCons(head.map(f), tail.lazyTransform(s -> s.map(f)));
  }

  @Override
  public <U> Stream<U> mapEval(Fn<T, IO<U>> f) {
    return SCons(head.flatMap(f), tail.lazyTransform(s -> s.mapEval(f)));
  }

  @Override
  public IO<Optional<IO<T>>> lazyHead() {
    return Pure(Optional.of(head));
  }

  @Override
  public IO<Optional<T>> headOption() {
    return head.map(Optional::of);
  }

  @Override
  public String toString() {
    return "Cons(" + head + "," + tail + ")";
  }
}

@RequiredArgsConstructor
class Defer<T> extends Stream<T> {

  final IO<Stream<T>> evalStream;

  @Override
  public <R> IO<R> foldRight(IO<R> z, Fn2<T, IO<R>, IO<R>> f) {
    return evalStream.flatMap(s -> s.foldRight(z, f));
  }

  @Override
  public <R> IO<R> collectRight(IO<R> z, Fn2<IO<T>, IO<R>, IO<R>> f) {
    return evalStream.flatMap(s -> s.collectRight(z, f));
  }

  @Override
  public <R> IO<R> foldLeft(R z, Fn2<R, T, R> f) {
    return evalStream.flatMap(s -> s.foldLeft(z, f));
  }

  @Override
  public <R> IO<R> collectLeft(IO<R> z, Fn2<IO<R>, IO<T>, IO<R>> f) {
    return evalStream.flatMap(s -> s.collectLeft(z, f));
  }

  @Override
  public Stream<T> take(int n) {
    return Defer(evalStream.map(s -> s.take(n)));
  }

  @Override
  public Stream<T> drop(int n) {
    return Defer(evalStream.map(s -> s.drop(n)));
  }

  @Override
  public Stream<T> takeWhile(Fn<T, Boolean> p, boolean includeFailure) {
    return Defer(evalStream.map(s -> s.takeWhile(p, includeFailure)));
  }

  @Override
  public Stream<T> dropWhile(Fn<T, Boolean> p) {
    return Defer(evalStream.map(s -> s.dropWhile(p)));
  }

  @Override
  public <U> Stream<U> flatMap(Fn<T, Stream<U>> f) {
    return Defer(evalStream.map(s -> s.flatMap(f)));
  }

  @Override
  public <U> Stream<U> map(Fn<T, U> f) {
    return Defer(evalStream.map(s -> s.map(f)));
  }

  @Override
  public <U> Stream<U> mapEval(Fn<T, IO<U>> f) {
    return Defer(evalStream.map(s -> s.mapEval(f)));
  }

  @Override
  public Stream<T> filter(Fn<T, Boolean> p) {
    return Defer(evalStream.map(s -> s.filter(p)));
  }

  @Override
  public IO<Optional<IO<T>>> lazyHead() {
    return evalStream.flatMap(Stream::lazyHead);
  }

  @Override
  public IO<Optional<T>> headOption() {
    return evalStream.flatMap(Stream::headOption);
  }

  @Override
  public String toString() {
    return "Defer(" + evalStream + ")";
  }
}

@NoArgsConstructor(access = AccessLevel.PRIVATE)
class Nil extends Stream<Object> {

  private static final Nil INSTANCE = new Nil();

  @SuppressWarnings("unchecked")
  static <T> Stream<T> instance() {
    return (Stream<T>) INSTANCE;
  }

  @Override
  public <R> IO<R> foldRight(IO<R> z, Fn2<Object, IO<R>, IO<R>> f) {
    return z;
  }

  @Override
  public <R> IO<R> collectLeft(IO<R> z, Fn2<IO<R>, IO<Object>, IO<R>> f) {
    return z;
  }

  @Override
  public <R> IO<R> collectRight(IO<R> z, Fn2<IO<Object>, IO<R>, IO<R>> f) {
    return z;
  }

  @Override
  public <R> IO<R> foldLeft(R z, Fn2<R, Object, R> f) {
    return Pure(z);
  }

  @Override
  public Stream<Object> take(int n) {
    return instance();
  }

  @Override
  public Stream<Object> drop(int n) {
    return instance();
  }

  @Override
  public Stream<Object> takeWhile(Fn<Object, Boolean> p, boolean includeFailure) {
    return instance();
  }

  @Override
  public Stream<Object> dropWhile(Fn<Object, Boolean> p) {
    return instance();
  }

  @Override
  public <U> Stream<U> flatMap(Fn<Object, Stream<U>> f) {
    return instance();
  }

  @Override
  public <U> Stream<U> map(Fn<Object, U> f) {
    return instance();
  }

  @Override
  public <U> Stream<U> mapEval(Fn<Object, IO<U>> f) {
    return instance();
  }

  @Override
  public Stream<Object> filter(Fn<Object, Boolean> p) {
    return instance();
  }

  @Override
  public IO<Optional<IO<Object>>> lazyHead() {
    return Pure(Optional.empty());
  }

  @Override
  public IO<Optional<Object>> headOption() {
    return Pure(Optional.empty());
  }

  @Override
  public String toString() {
    return "Nil";
  }
}
