package com.community.meal.algorithms;

import java.math.*;
import java.util.*;

public record RankedCandidate(long id, BigDecimal score, ScoreComponents components) {}
