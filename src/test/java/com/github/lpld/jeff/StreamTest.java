package com.github.lpld.jeff;

import com.github.lpld.jeff.LList.LCons;
import com.github.lpld.jeff.LList.LNil;

import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import static com.github.lpld.jeff.IO.IO;
import static com.github.lpld.jeff.IO.Pure;
import static com.github.lpld.jeff.Stream.Cons;
import static com.github.lpld.jeff.Stream.Nil;
import static com.github.lpld.jeff.Stream.SCons;
import static com.github.lpld.jeff.Stream.Stream;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author leopold
 * @since 8/10/18
 */
public class StreamTest {

  // Stream(1, 2, 3, 4, 5)
  private static List<Stream<Integer>> streams = buildStreams(1, 2, 3, 4, 5);

  @SafeVarargs
  private static <T> List<Stream<T>> buildStreams(T... values) {
    LList<IO<T>> v = LNil.instance();
    for (int i = values.length - 1; i >= 0; i--) {
      v = v.prepend(Pure(values[i]));
    }
    return buildStreams(v);
  }

  @SafeVarargs
  private static <T> List<Stream<T>> buildStreams(IO<T>... values) {
    return buildStreams(LList.of(values));
  }

  private static <T> List<Stream<T>> buildStreams(LList<IO<T>> values) {
    final ArrayList<Stream<T>> list = new ArrayList<>();

    if (values.isEmpty()) {
      return Collections.singletonList(Nil());
    }

    final LCons<IO<T>> ios = (LCons<IO<T>>) values;
    final IO<T> head = ((LCons<IO<T>>) values).head;

    for (Stream<T> child : buildStreams(ios.tail)) {
      list.add(SCons(head, child));
//      list.add(SSnoc(Pure(child.reverse()), head).reverse());
//      list.add(Concat(Nil(), SCons(head, Pure(child))));
//      list.add(Concat(SCons(head, Pure(child)), Nil()));
    }

    return list;
  }

  @Test
  public void testNil() {

    assertThat(Nil(), is(instanceOf(Nil.class)));
    assertThat(Nil().toLList().run(), is(equalTo(LList.of())));
  }

  @Test
  public void testRepeat() {
    final Stream<Integer> repeat = Stream(1, 2).repeat();
    assertThat(repeat.take(9).toLList().run(), equalTo(LList.of(1, 2, 1, 2, 1, 2, 1, 2, 1)));

    final AtomicInteger counter = new AtomicInteger();
    final Stream<Integer> ss = Stream.eval(IO(counter::incrementAndGet));
//    ss.foldRight(ss, Stream::Cons);
    ss.repeat().take(1).toLList().run();
    assertThat(counter.get(), is(42));

    final Stream<Integer> stream1 = Cons(1, Stream(5).repeat().take(2)).repeat();
    final LList<Integer> result = Stream(77).repeat()
        .flatMap(i -> stream1)
        .take(10)
        .toLList()
        .run();
    assertThat(result, equalTo(LList.of(1, 5, 5, 1, 5, 5, 1, 5, 5, 1)));
  }

  @Test
//  @Ignore("two following cases hang. Apparently drop/take algorithms should be revisited.")
  public void testRepeatDropTake() {
    Stream(1).repeat()
        .takeWhile(i -> i < 10)
        .head()
        .toLList()
        .run();

    // this looks rather simple, but it still hangs.
    // it looks that our collectS method is not suitable for such operations
    Stream(1).repeat()
        .drop(1)
        .take(1)
        .toLList()
        .run();
  }

  @Test
  public void testToLList() {

    for (Stream<Integer> stream : streams) {
      final LList<Integer> result = stream
          .toLList()
          .run();

      assertThat(result, is(equalTo(LList.of(1, 2, 3, 4, 5))));
    }
  }

  @Test
  public void testStreamOf() {
    assertThat(
        Stream(1, 2, 3, 4, 5)
            .toLList()
            .run(),
        equalTo(LList.of(1, 2, 3, 4, 5))
    );
  }

  @Test
  public void testCollect() {
    final Stream<Integer> stream = Nil();

    stream
        .take(1)
        .toLList()
        .run();
  }

  @Test
  public void testTake() {
    for (Stream<Integer> stream : streams) {
      assertThat(stream.take(0).toLList().run(), equalTo(LList.of()));
      assertThat(stream.take(1).toLList().run(), equalTo(LList.of(1)));
      assertThat(stream.take(5).toLList().run(), equalTo(LList.of(1, 2, 3, 4, 5)));
      assertThat(stream.take(10).toLList().run(), equalTo(LList.of(1, 2, 3, 4, 5)));
    }
  }

  @Test
  public void testDrop() {
    for (Stream<Integer> stream : streams) {
      assertThat(stream.drop(0).toLList().run(), equalTo(LList.of(1, 2, 3, 4, 5)));
      assertThat(stream.drop(1).toLList().run(), equalTo(LList.of(2, 3, 4, 5)));
      assertThat(stream.drop(2).toLList().run(), equalTo(LList.of(3, 4, 5)));
      assertThat(stream.drop(5).toLList().run(), equalTo(LList.of()));
      assertThat(stream.drop(10).toLList().run(), equalTo(LList.of()));
    }
  }

  @Test
  public void testStreamEval() {
    assertThat(Stream.eval(Pure(5)).toLList().run(), equalTo(LList.of(5)));
  }

  @Test
  public void testStreamEval1() {
    final AtomicBoolean evaluated = new AtomicBoolean();
    final IO<Integer> io = IO(() -> {
      evaluated.set(true);
      return 4;
    });
    final Stream<Integer> str = SCons(Pure(3), SCons(io, SCons(Pure(6), Nil())));
    str.tail().tail().toLList().run();
    assertThat(evaluated.get(), is(false));
  }

  @Test
  public void testStreamLazinessTakeDrop() {
    final AtomicBoolean evaluated4 = new AtomicBoolean();
    final AtomicBoolean evaluated6 = new AtomicBoolean();

    final BiConsumer<Boolean, Boolean> validate = (ev4, ev6) -> {
      assertThat(evaluated4.get(), is(ev4));
      assertThat(evaluated6.get(), is(ev6));

      evaluated4.set(false);
      evaluated6.set(false);
    };

    final IO<Integer> io4 = IO(() -> {
      evaluated4.set(true);
      return 4;
    });

    final IO<Integer> io6 = IO(() -> {
      evaluated6.set(true);
      return 6;
    });

    final List<Stream<Integer>> streams =
        buildStreams(Pure(2), Pure(3), io4, Pure(5), io6, Pure(7));

    for (Stream<Integer> stream : streams) {
      stream.head().toLList().run();
      validate.accept(false, false);

      stream.tail().head().toLList().run();
      validate.accept(false, false);

      stream.tail().tail().tail().head().toLList().run();
      validate.accept(false, false);

      stream.drop(4).head().toLList().run();
      validate.accept(false, true);

      stream.drop(2).head().toLList().run();
      validate.accept(true, false);

      assertThat(stream.takeWhile(i -> i <= 4).toLList().run(), equalTo(LList.of(2, 3, 4)));
      validate.accept(true, false);

      stream.dropWhile(i -> i <= 4).head().toLList().run();
      validate.accept(true, false);

      stream.dropWhile(i -> i <= 4).tail().head().toLList().run();
      validate.accept(true, true);

      assertThat(stream.dropWhile(i -> i <= 4).tail().tail().head().toLList().run(),
                 equalTo(LList.of(7)));
      validate.accept(true, false);
    }
  }

  @Test
  public void testEval3() {
    final AtomicBoolean evaluated = new AtomicBoolean();
    final IO<Integer> io = IO(() -> {
      evaluated.set(true);
      return 1;
    });

    final Stream<Integer> stream = SCons(io, Cons(10, Nil()));

    final Stream<Integer> newStream = stream.flatMap(i -> Stream(i, i + 1));

    assertThat(newStream.drop(2).toLList().run(), equalTo(LList.of(10, 11)));

    // the following assertion fails:
    // assertThat(evaluated, is(false));

    // but it looks that we cannot avoid it. we MUST evaluate `io` in order to execute drop(2),
    // because otherwise we won't know the size of the stream.
  }

  @Test
  public void testFoldRight() {

    for (Stream<Integer> stream : streams) {
      final LList<Integer> result = stream
          .foldRight(LNil.<Integer>instance(), (el, ll) -> ll.prepend(el))
          .run();

      assertThat(result, is(equalTo(LList.of(1, 2, 3, 4, 5))));
    }
  }

  @Test
  public void testExists() {
    final AtomicLong counter = new AtomicLong();
    final Stream<Integer> stream =
        Stream(1, 2, 3, 4, 5)
            .mapEval(i -> IO(() -> {
              counter.incrementAndGet();
              return i;
            }));

    // exists is implemented using foldRight, so it should be lazy:

    assertThat(stream.exists(elem1 -> elem1 == 3).run(), is(true));
    assertThat(counter.get(), is(equalTo(3L)));

    counter.set(0);

    assertThat(stream.exists(elem -> elem < 0).run(), is(false));
    assertThat(counter.get(), is(equalTo(5L)));
  }

  @Test
  public void testFoldLeft() {

    for (Stream<Integer> stream : streams) {
      final LList<Integer> result = stream
          .foldLeft(LNil.<Integer>instance(), LList::prepend)
          .run();

      assertThat(result, is(equalTo(LList.of(5, 4, 3, 2, 1))));
    }
  }

//  @Test
//  public void testScanRight() {
//    for (Stream<Integer> stream : streams) {
//      final LList<Integer> list = stream.scanRight(0, (elem, z) -> elem)
//          .toLList()
//          .run();
//
//      assertThat(list, is(equalTo(LList.of(1, 2, 3, 4, 5))));
//    }
//  }
//
//  @Test
//  public void testScanLeft() {
//    for (Stream<Integer> stream : streams) {
//      final LList<Integer> list = stream.scanLeft(0, (z, elem) -> elem)
//          // toList
//          .foldRight(LNil.<Integer>instance(), (el, ll) -> ll.prepend(el))
//          .run();
//
//      assertThat(list, is(equalTo(LList.of(5, 4, 3, 2, 1))));
//    }
//  }

  public void testUnfold() {

  }
}
