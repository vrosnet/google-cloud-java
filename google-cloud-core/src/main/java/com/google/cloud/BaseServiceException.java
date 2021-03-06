/*
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.gax.grpc.ApiException;
import com.google.common.base.MoreObjects;

import java.io.IOException;
import java.io.Serializable;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Base class for all service exceptions.
 */
public class BaseServiceException extends RuntimeException {

  private static final long serialVersionUID = 759921776378760835L;
  public static final int UNKNOWN_CODE = 0;

  private final int code;
  private final boolean retryable;
  private final String reason;
  private final boolean idempotent;
  private final String location;
  private final String debugInfo;

  protected static final class Error implements Serializable {

    private static final long serialVersionUID = -4019600198652965721L;

    private final Integer code;
    private final String reason;
    private final boolean rejected;

    public Error(Integer code, String reason) {
      this(code, reason, false);
    }

    public Error(Integer code, String reason, boolean rejected) {
      this.code = code;
      this.reason = reason;
      this.rejected = rejected;
    }

    /**
     * Returns the code associated with this exception.
     */
    public Integer code() {
      return code;
    }

    /**
     * Returns true if the error indicates that the API call was certainly not accepted by the
     * server. For instance, if the server returns a rate limit exceeded error, it certainly did not
     * process the request and this method will return {@code true}.
     */
    public boolean rejected() {
      return rejected;
    }

    /**
     * Returns the reason that caused the exception.
     */
    public String reason() {
      return reason;
    }

    boolean isRetryable(boolean idempotent, Set<Error> retryableErrors) {
      for (Error retryableError : retryableErrors) {
        if ((retryableError.code() == null || retryableError.code().equals(this.code()))
            && (retryableError.reason() == null || retryableError.reason().equals(this.reason()))) {
          return idempotent || retryableError.rejected();
        }
      }
      return false;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("code", code).add("reason", reason).toString();
    }

    @Override
    public int hashCode() {
      return Objects.hash(code, reason);
    }
  }

  public BaseServiceException(IOException exception, boolean idempotent) {
    super(message(exception), exception);
    int code = UNKNOWN_CODE;
    String reason = null;
    String location = null;
    String debugInfo = null;
    Boolean retryable = null;
    if (exception instanceof GoogleJsonResponseException) {
      GoogleJsonError jsonError = ((GoogleJsonResponseException) exception).getDetails();
      if (jsonError != null) {
        Error error = new Error(jsonError.getCode(), reason(jsonError));
        code = error.code;
        reason = error.reason;
        retryable = isRetryable(idempotent, error);
        if (reason != null) {
          GoogleJsonError.ErrorInfo errorInfo = jsonError.getErrors().get(0);
          location = errorInfo.getLocation();
          debugInfo = (String) errorInfo.get("debugInfo");
        }
      } else {
        code = ((GoogleJsonResponseException) exception).getStatusCode();
      }
    }
    this.retryable = MoreObjects.firstNonNull(retryable, isRetryable(idempotent, exception));
    this.code = code;
    this.reason = reason;
    this.idempotent = idempotent;
    this.location = location;
    this.debugInfo = debugInfo;
  }

  public BaseServiceException(GoogleJsonError googleJsonError, boolean idempotent) {
    super(googleJsonError.getMessage());
    Error error = new Error(googleJsonError.getCode(), reason(googleJsonError));
    this.code = error.code;
    this.reason = error.reason;
    this.retryable = isRetryable(idempotent, error);
    if (this.reason != null) {
      GoogleJsonError.ErrorInfo errorInfo = googleJsonError.getErrors().get(0);
      this.location = errorInfo.getLocation();
      this.debugInfo = (String) errorInfo.get("debugInfo");
    } else {
      this.location = null;
      this.debugInfo = null;
    }
    this.idempotent = idempotent;
  }

  public BaseServiceException(int code, String message, String reason, boolean idempotent) {
    this(code, message, reason, idempotent, null);
  }

  public BaseServiceException(int code, String message, String reason, boolean idempotent,
      Throwable cause) {
    super(message, cause);
    this.code = code;
    this.reason = reason;
    this.idempotent = idempotent;
    this.retryable = isRetryable(idempotent, new Error(code, reason));
    this.location = null;
    this.debugInfo = null;
  }

  public BaseServiceException(ApiException apiException, boolean idempotent) {
    super(apiException.getMessage(), apiException);
    this.code = apiException.getStatusCode().value();
    this.reason = apiException.getStatusCode().name();
    this.idempotent = idempotent;
    this.retryable = apiException.isRetryable();
    this.location = null;
    this.debugInfo = null;
  }

  protected Set<Error> retryableErrors() {
    return Collections.emptySet();
  }

  protected boolean isRetryable(boolean idempotent, Error error) {
    return error.isRetryable(idempotent, retryableErrors());
  }

  protected boolean isRetryable(boolean idempotent, IOException exception) {
    boolean exceptionIsRetryable = exception instanceof SocketTimeoutException
        || exception instanceof SocketException
        || "insufficient data written".equals(exception.getMessage());
    return idempotent && exceptionIsRetryable;
  }

  /**
   * Returns the code associated with this exception.
   */
  public int code() {
    return code;
  }

  /**
   * Returns the reason that caused the exception.
   */
  public String reason() {
    return reason;
  }

  /**
   * Returns {@code true} when it is safe to retry the operation that caused this exception.
   */
  public boolean retryable() {
    return retryable;
  }

  /**
   * Returns {@code true} when the operation that caused this exception had no side effects.
   */
  public boolean idempotent() {
    return idempotent;
  }

  /**
   * Returns the service location where the error causing the exception occurred. Returns {@code
   * null} if not available.
   */
  public String location() {
    return location;
  }

  protected String debugInfo() {
    return debugInfo;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof BaseServiceException)) {
      return false;
    }
    BaseServiceException other = (BaseServiceException) obj;
    return Objects.equals(getCause(), other.getCause())
        && Objects.equals(getMessage(), other.getMessage())
        && code == other.code
        && retryable == other.retryable
        && Objects.equals(reason, other.reason)
        && idempotent == other.idempotent
        && Objects.equals(location, other.location)
        && Objects.equals(debugInfo, other.debugInfo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getCause(), getMessage(), code, retryable, reason, idempotent, location,
        debugInfo);
  }

  private static String reason(GoogleJsonError error) {
    if (error.getErrors() != null && !error.getErrors().isEmpty()) {
      return error.getErrors().get(0).getReason();
    }
    return null;
  }

  private static String message(IOException exception) {
    if (exception instanceof GoogleJsonResponseException) {
      GoogleJsonError details = ((GoogleJsonResponseException) exception).getDetails();
      if (details != null) {
        return details.getMessage();
      }
    }
    return exception.getMessage();
  }

  protected static void translateAndPropagateIfPossible(RetryHelper.RetryHelperException ex) {
    if (ex.getCause() instanceof BaseServiceException) {
      throw (BaseServiceException) ex.getCause();
    }
    if (ex instanceof RetryHelper.RetryInterruptedException) {
      RetryHelper.RetryInterruptedException.propagate();
    }
  }
}
