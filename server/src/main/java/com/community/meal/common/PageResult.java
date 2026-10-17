package com.community.meal.common;

import java.util.List;

public record PageResult<T>(List<T> records, long total) {}
