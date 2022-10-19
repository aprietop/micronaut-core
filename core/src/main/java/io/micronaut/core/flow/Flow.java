/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.flow;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The flow class represents a data flow which state can be represented as a simple imperative flow or an async/reactive.
 * The state can be resolved or lazy - based on the implementation.
 * NOTE: The instance of the flow is not supposed to be used after a mapping operator is used.
 *
 * @param <T> The flow type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public interface Flow<T> {

    /**
     * Create a simple flow representing a value.
     *
     * @param value The value
     * @param <K>   The value type
     * @return a new flow
     */
    @NonNull
    static <K> Flow<K> just(@Nullable K value) {
        return (Flow<K>) new ImperativeFlowImpl(value, null);
    }

    /**
     * Create a simple flow representing an error.
     *
     * @param e   The exception
     * @param <K> The value type
     * @return a new flow
     */
    @NonNull
    static <K> Flow<K> error(@NonNull Throwable e) {
        return (Flow<K>) new ImperativeFlowImpl(null, e);
    }

    /**
     * Create a simple flow representing an empty state.
     *
     * @param <T>      The flow value type
     * @return a new flow
     */
    @NonNull
    static <T> Flow<T> empty() {
        return (Flow<T>) new ImperativeFlowImpl(null, null);
    }

    /**
     * Create a flow by invoking a supplier asynchronously.
     *
     * @param executor The executor
     * @param supplier The supplier
     * @param <T>      The flow value type
     * @return a new flow
     */
    @NonNull
    static <T> Flow<T> async(@NonNull Executor executor, @NonNull Supplier<? extends Flow<T>> supplier) {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        executor.execute(() -> supplier.get().onComplete((t, throwable) -> {
            if (throwable != null) {
                completableFuture.completeExceptionally(throwable);
            } else {
                completableFuture.complete(t);
            }
        }));
        return CompletableFutureFlow.just(completableFuture);
    }

    /**
     * Map a not-empty value.
     *
     * @param transformer The value transformer
     * @param <R>         New value Type
     * @return a new flow
     */
    @NonNull
    <R> Flow<R> map(@NonNull Function<? super T, ? extends R> transformer);

    /**
     * Map a not-empty value to a new flow.
     *
     * @param transformer The value transformer
     * @param <R>         New value Type
     * @return a new flow
     */
    @NonNull
    <R> Flow<R> flatMap(@NonNull Function<? super T, ? extends Flow<? extends R>> transformer);

    /**
     * Supply a new flow after the existing flow value is resolved.
     *
     * @param supplier The supplier
     * @param <R>      New value Type
     * @return a new flow
     */
    @NonNull
    <R> Flow<R> then(@NonNull Supplier<? extends Flow<? extends R>> supplier);

    /**
     * Supply a new flow if the existing flow is erroneous.
     *
     * @param fallback The fallback
     * @return a new flow
     */
    @NonNull
    Flow<T> onErrorResume(@NonNull Function<? super Throwable, ? extends Flow<? extends T>> fallback);

    /**
     * Store a contextual value.
     *
     * @param key   The key
     * @param value The value
     * @return a new flow
     */
    @NonNull
    Flow<T> putInContext(@NonNull String key, @NonNull Object value);

    /**
     * Invokes a provided function when the flow is resolved.
     *
     * @param fn The function
     */
    void onComplete(@NonNull BiConsumer<? super T, Throwable> fn);

    /**
     * Converts the existing flow into the completable future.
     *
     * @return a {@link CompletableFuture} that represents the state if this flow.
     */
    @NonNull
    default CompletableFuture<T> toCompletableFuture() {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        onComplete((value, throwable) -> {
            if (throwable != null) {
                CompletableFuture.failedFuture(throwable);
            }
            CompletableFuture.completedFuture(value);
        });
        return completableFuture;
    }

}

