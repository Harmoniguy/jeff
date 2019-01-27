package com.github.lpld.jeff_examples.tetris;

import com.github.lpld.jeff.Stream;
import com.github.lpld.jeff_examples.tetris.Tetris.Move;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;

import io.vavr.control.Option;
import lombok.Getter;

import static com.github.lpld.jeff.IO.IO;
import static io.vavr.API.Some;

/**
 * @author leopold
 * @since 2019-01-27
 */
public class UserInput {

  @Getter
  private final Stream<Move> moves;

  public UserInput() throws IOException {
    final Terminal term = TerminalBuilder.builder()
        .jansi(true)
        .system(true)
        .build();

    term.enterRawMode();

    final NonBlockingReader reader = term.reader();

    moves = Stream.eval(IO(() -> reader.read()))
        .repeat()
        .map(input -> {
          switch ((char) input.intValue()) {
            case 'w':
              return Some(Move.ROTATE);
            case 'd':
              return Some(Move.RIGHT);
            case 'a':
              return Some(Move.LEFT);
            case 's':
              return Some(Move.DOWN);
            default:
              return Option.<Move>none();
          }
        })
        .filter(Option::isDefined)
        .map(Option::get);

  }


}
