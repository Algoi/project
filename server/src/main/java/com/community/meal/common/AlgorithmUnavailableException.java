package com.community.meal.common;

/** Only computation failures are recoverable; authorization and storage errors must propagate. */
public final class AlgorithmUnavailableException extends ApiException {
  public AlgorithmUnavailableException() {
    super(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"ALGORITHM_UNAVAILABLE","预测计算暂不可用");
  }
}
