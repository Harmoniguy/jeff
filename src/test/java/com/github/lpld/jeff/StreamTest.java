package com.github.lpld.jeff;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.github.lpld.jeff.IO.IO;
import static com.github.lpld.jeff.IO.Pure;
import static com.github.lpld.jeff.Stream.Concat;
import static com.github.lpld.jeff.Stream.Cons;
import static com.github.lpld.jeff.Stream.Nil;
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
  private static List<Stream<Integer>> streams = Arrays.asList(
      Cons(1, Cons(2, Cons(3, Cons(4, Cons(5, Nil()))))),

      Concat(Cons(1, Cons(2, Nil())),
             Cons(3, Cons(4, Cons(5, Nil())))),

      Concat(Nil(),
             Concat(Cons(1, Cons(2, Nil())),
                    Cons(3, Cons(4, Cons(5, Nil()))))),

      Concat(Concat(Cons(1, Cons(2, Nil())),
                    Concat(Cons(3, Cons(4, Nil())),
                           Concat(Cons(5, Nil()),
                                  Nil()))),
             Nil())
  );

  @Test
  public void testNil() {
    assertThat(Nil(), is(instanceOf(Nil.class)));
    assertThat(Nil().toLList().run(), is(equalTo(LList.of())));
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
  public void testOfOptional() {
    assertThat(Stream.ofOptional(Optional.empty()).toLList().run(), equalTo(LList.of()));
    assertThat(Stream.ofOptional(Optional.of(5)).toLList().run(), equalTo(LList.of(5)));
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
  public void testStreamEval() {
    assertThat(Stream.eval(Pure(5)).toLList().run(), equalTo(LList.of(5)));

    final AtomicBoolean evaluated = new AtomicBoolean();
    final Stream<Integer> stream = Stream.eval(IO(() -> {
      evaluated.set(true);
      return 5;
    }));
    assertThat(evaluated.get(), is(false));

    final Stream<Integer> newStream = Cons(4, stream);
    assertThat(evaluated.get(), is(false));

    final LList<Integer> head = newStream.head().toLList().run();
    assertThat(evaluated.get(), is(false));

    final Stream<Integer> tail = newStream.tail();
    assertThat(evaluated.get(), is(false));

    assertThat(newStream.toLList().run(), equalTo(LList.of(4, 5)));
    assertThat(evaluated.get(), is(true));
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

  @Test
  public void testScanRight() {
    for (Stream<Integer> stream : streams) {
      final LList<Integer> list = stream.scanRight(0, (elem, z) -> elem)
          .toLList()
          .run();

      assertThat(list, is(equalTo(LList.of(1, 2, 3, 4, 5))));
    }
  }

  @Test
  public void testScanLeft() {
    for (Stream<Integer> stream : streams) {
      final LList<Integer> list = stream.scanLeft(0, (z, elem) -> elem)
          // toList
          .foldRight(LNil.<Integer>instance(), (el, ll) -> ll.prepend(el))
          .run();

      assertThat(list, is(equalTo(LList.of(5, 4, 3, 2, 1))));
    }
  }

  public void testUnfold() {

  }
}
