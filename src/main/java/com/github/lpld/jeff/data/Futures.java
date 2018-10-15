package com.github.lpld.jeff.data;

import com.github.lpld.jeff.functions.Xn0;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * @author leopold
 * @since 12/10/18
 */
public final class Futures {

  public static <T> CompletableFuture<T> failed(Throwable err) {
    final CompletableFuture<T> future = new CompletableFuture<>();
    future.completeExceptionally(err);
    return future;
  }

  public static <T> CompletableFuture<T> run(Xn0<CompletableFuture<T>> run) {
    try {
      return run.ap();
    } catch (Throwable err) {
      return failed(err);
    }
  }

  public static <T> CompletableFuture<T> run(Xn0<T> run, Executor executor) {
    // Arrr I hate JDK guys for this!
    // Why not just CompletableFuture.supplyAsync(run::ap, executor) ???

    final CompletableFuture<T> future = new CompletableFuture<>();
    executor.execute(() -> {
      try {
        future.complete(run.ap());
      } catch (Throwable err) {
        future.completeExceptionally(err);
      }
    });
    return future;
  }
}
