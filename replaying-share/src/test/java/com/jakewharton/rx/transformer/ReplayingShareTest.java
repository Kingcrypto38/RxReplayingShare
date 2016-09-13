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

import com.jakewharton.rxrelay.PublishRelay;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.observers.TestSubscriber;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ReplayingShareTest {
  @Test public void noInitialValue() {
    PublishRelay<String> relay = PublishRelay.create();
    Observable<String> observable = relay.compose(ReplayingShare.<String>instance());

    TestSubscriber<String> subscriber = new TestSubscriber<>();
    observable.subscribe(subscriber);
    subscriber.assertNoValues();
  }

  @Test public void initialValueToNewSubscriber() {
    PublishRelay<String> relay = PublishRelay.create();
    Observable<String> observable = relay.compose(ReplayingShare.<String>instance());

    TestSubscriber<String> subscriber1 = new TestSubscriber<>();
    observable.subscribe(subscriber1);
    subscriber1.assertNoValues();

    relay.call("Foo");
    subscriber1.assertValues("Foo");

    TestSubscriber<String> subscriber2 = new TestSubscriber<>();
    observable.subscribe(subscriber2);
    subscriber2.assertValues("Foo");
  }

  @Test public void initialValueToNewSubscriberAfterUnsubscribe() {
    PublishRelay<String> relay = PublishRelay.create();
    Observable<String> observable = relay.compose(ReplayingShare.<String>instance());

    TestSubscriber<String> subscriber1 = new TestSubscriber<>();
    Subscription subscription1 = observable.subscribe(subscriber1);
    subscriber1.assertNoValues();

    relay.call("Foo");
    subscriber1.assertValues("Foo");
    subscription1.unsubscribe();

    TestSubscriber<String> subscriber2 = new TestSubscriber<>();
    observable.subscribe(subscriber2);
    subscriber2.assertValues("Foo");
  }

  @Test public void valueMissedWhenNoSubscribers() {
    PublishRelay<String> relay = PublishRelay.create();
    Observable<String> observable = relay.compose(ReplayingShare.<String>instance());

    TestSubscriber<String> subscriber1 = new TestSubscriber<>();
    Subscription subscription1 = observable.subscribe(subscriber1);
    subscriber1.assertNoValues();
    subscription1.unsubscribe();

    relay.call("Foo");
    subscriber1.assertNoValues();

    TestSubscriber<String> subscriber2 = new TestSubscriber<>();
    observable.subscribe(subscriber2);
    subscriber2.assertNoValues();
  }

  @Test public void fatalExceptionDuringReplayThrown() {
    PublishRelay<String> relay = PublishRelay.create();
    Observable<String> observable = relay.compose(ReplayingShare.<String>instance());

    observable.subscribe();
    relay.call("Foo");

    Action1<String> brokenAction = new Action1<String>() {
      @Override public void call(String s) {
        throw new OutOfMemoryError("broken!");
      }
    };
    try {
      observable.subscribe(brokenAction);
    } catch (OutOfMemoryError e) {
      assertEquals("broken!", e.getMessage());
    }
  }

  @Test public void nonFatalExceptionDuringReplayPropagated() {
    PublishRelay<String> relay = PublishRelay.create();
    Observable<String> observable = relay.compose(ReplayingShare.<String>instance());

    observable.subscribe();
    relay.call("Foo");

    // Use unsafeSubscribe here since SafeSubscriber automatically propagates exceptions.
    observable.unsafeSubscribe(new Subscriber<String>() {
      @Override public void onError(Throwable e) {
        assertTrue(e instanceof IllegalStateException);
        assertEquals("broken!", e.getMessage());
      }

      @Override public void onNext(String s) {
        throw new IllegalStateException("broken!");
      }

      @Override public void onCompleted() {
        throw new AssertionError();
      }
    });
  }

  @Test public void refCountToUpstream() {
    PublishRelay<String> relay = PublishRelay.create();

    final AtomicInteger count = new AtomicInteger();
    Observable<String> observable = relay //
        .doOnSubscribe(new Action0() {
          @Override public void call() {
            count.incrementAndGet();
          }
        }) //
        .doOnUnsubscribe(new Action0() {
          @Override public void call() {
            count.decrementAndGet();
          }
        }) //
        .compose(ReplayingShare.<String>instance());

    Subscription subscription1 = observable.subscribe(new TestSubscriber<String>());
    assertEquals(1, count.get());

    Subscription subscription2 = observable.subscribe(new TestSubscriber<String>());
    assertEquals(1, count.get());

    Subscription subscription3 = observable.subscribe(new TestSubscriber<String>());
    assertEquals(1, count.get());

    subscription1.unsubscribe();
    assertEquals(1, count.get());

    subscription3.unsubscribe();
    assertEquals(1, count.get());

    subscription2.unsubscribe();
    assertEquals(0, count.get());
  }

  @Test public void backpressureHonoredWhenCached() {
    PublishRelay<String> relay = PublishRelay.create();
    Observable<String> observable = relay.compose(ReplayingShare.<String>instance());

    TestSubscriber<String> subscriber1 = new TestSubscriber<>();
    observable.subscribe(subscriber1);
    subscriber1.assertNoValues();

    relay.call("Foo");
    subscriber1.assertValues("Foo");

    TestSubscriber<String> subscriber2 = new TestSubscriber<>(0);
    observable.subscribe(subscriber2);
    subscriber2.assertNoValues();

    relay.call("Bar"); // Replace the cached value...
    subscriber1.assertValues("Foo", "Bar");

    subscriber2.requestMore(1); // ...and ensure new requests see it.
    subscriber2.assertValues("Bar");
  }

  @Test public void streamsDoNotShareInstances() {
    PublishRelay<String> relayA = PublishRelay.create();
    Observable<String> observableA = relayA.compose(ReplayingShare.<String>instance());
    TestSubscriber<String> subscriberA1 = new TestSubscriber<>();
    observableA.subscribe(subscriberA1);

    PublishRelay<String> relayB = PublishRelay.create();
    Observable<String> observableB = relayB.compose(ReplayingShare.<String>instance());
    TestSubscriber<String> subscriberB1 = new TestSubscriber<>();
    observableB.subscribe(subscriberB1);

    relayA.call("Foo");
    subscriberA1.assertValues("Foo");
    relayB.call("Bar");
    subscriberB1.assertValues("Bar");

    TestSubscriber<String> subscriberA2 = new TestSubscriber<>();
    observableA.subscribe(subscriberA2);
    subscriberA2.assertValues("Foo");

    TestSubscriber<String> subscriberB2 = new TestSubscriber<>();
    observableB.subscribe(subscriberB2);
    subscriberB2.assertValues("Bar");
  }
}
