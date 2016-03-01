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
package com.jakewharton.rx.transformer;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Observable.Transformer;
import rx.Subscriber;
import rx.exceptions.Exceptions;
import rx.functions.Action1;

/**
 * A transformer which combines the {@code replay(1)}, {@code publish()}, and {@code refCount()}
 * operators.
 *
 * Unlike traditional combinations of these operators, `ReplayingShare` caches the last emitted
 * value from the upstream observable *only* when one or more downstream subscribers are connected.
 * This allows expensive upstream observables to be shut down when no one is subscribed while also
 * replaying the last value seen by *any* subscriber to new ones.
 */
public final class ReplayingShare<T> implements Transformer<T, T> {
  private static final ReplayingShare<Object> INSTANCE = new ReplayingShare<>();

  /** The singleton instance of this transformer. */
  @SuppressWarnings("unchecked") // Safe because of erasure.
  public static <T> ReplayingShare<T> instance() {
    return (ReplayingShare<T>) INSTANCE;
  }

  private ReplayingShare() {
  }

  @Override public Observable<T> call(Observable<T> upstream) {
    LastSeen<T> lastSeen = new LastSeen<>();
    return upstream.doOnNext(lastSeen.consumer).share().startWith(lastSeen.producer);
  }

  private static final class LastSeen<T> {
    private static final Object NONE = new Object();

    @SuppressWarnings("unchecked") // Safe because of erasure.
    volatile T last = (T) NONE;

    LastSeen() {
    }

    final Action1<T> consumer = new Action1<T>() {
      @Override public void call(T latest) {
        last = latest;
      }
    };
    final Observable<T> producer = Observable.create(new OnSubscribe<T>() {
      @Override public void call(Subscriber<? super T> subscriber) {
        T value = last;
        if (value != NONE) {
          try {
            subscriber.onNext(value);
          } catch (Throwable t) {
            Exceptions.throwIfFatal(t);
            subscriber.onError(t);
            return;
          }
        }
        subscriber.onCompleted();
      }
    });
  }
}
