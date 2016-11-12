/*
 * Copyright 2016 Jake Wharton
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jakewharton.rx;

import io.reactivex.Flowable;
import io.reactivex.FlowableTransformer;
import io.reactivex.Observable;
import io.reactivex.ObservableTransformer;
import io.reactivex.functions.Consumer;
import java.util.concurrent.Callable;

/**
 * A transformer which combines the {@code replay(1)}, {@code publish()}, and {@code refCount()}
 * operators.
 * <p>
 * Unlike traditional combinations of these operators, `ReplayingShare` caches the last emitted
 * value from the upstream observable or flowable *only* when one or more downstream subscribers
 * are connected. This allows expensive upstream sources to be shut down when no one is listening
 * while also replaying the last value seen by *any* subscriber to new ones.
 */
public final class ReplayingShare<T>
    implements ObservableTransformer<T, T>, FlowableTransformer<T, T> {
  private static final ReplayingShare<Object> INSTANCE = new ReplayingShare<>();

  /** The singleton instance of this transformer. */
  @SuppressWarnings("unchecked") // Safe because of erasure.
  public static <T> ReplayingShare<T> instance() {
    return (ReplayingShare<T>) INSTANCE;
  }

  private ReplayingShare() {
  }

  @Override public Observable<T> apply(Observable<T> upstream) {
    ObservableLastSeen<T> lastSeen = new ObservableLastSeen<>();
    return upstream.doOnNext(lastSeen).share().startWith(Observable.defer(lastSeen));
  }

  @Override public Flowable<T> apply(Flowable<T> upstream) {
    FlowableLastSeen<T> lastSeen = new FlowableLastSeen<>();
    return upstream.doOnNext(lastSeen).share().startWith(Flowable.defer(lastSeen));
  }

  static final class ObservableLastSeen<T> implements Consumer<T>, Callable<Observable<T>> {
    private volatile T last;

    @Override public void accept(T latest) {
      last = latest;
    }

    @Override public Observable<T> call() {
      T value = last;
      return value != null
          ? Observable.just(value)
          : Observable.<T>empty();
    }
  }

  static final class FlowableLastSeen<T> implements Consumer<T>, Callable<Flowable<T>> {
    private volatile T last;

    @Override public void accept(T latest) {
      last = latest;
    }

    @Override public Flowable<T> call() {
      T value = last;
      return value != null
          ? Flowable.just(value)
          : Flowable.<T>empty();
    }
  }
}
