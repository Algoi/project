package com.community.meal.contracts;
/** Persisted audit identifier is also the event deduplication key. Delivered in the source transaction. */
public record BusinessEvent(long id,long actorId,String action,String resource,String resourceId) {}
