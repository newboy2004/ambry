/**
 * Copyright 2016 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
package com.github.ambry.admin;

import com.codahale.metrics.MetricRegistry;
import com.github.ambry.config.VerifiableProperties;
import com.github.ambry.rest.IdConverter;
import com.github.ambry.rest.IdConverterFactory;
import com.github.ambry.rest.RestMethod;
import com.github.ambry.rest.RestRequest;
import com.github.ambry.rest.RestServiceErrorCode;
import com.github.ambry.rest.RestServiceException;
import com.github.ambry.router.Callback;
import com.github.ambry.router.FutureResult;
import java.util.concurrent.Future;


/**
 * Factory that instantiates an {@link IdConverter} implementation for the Admin.
 */
public class AdminIdConverterFactory implements IdConverterFactory {

  private final AdminMetrics adminMetrics;

  public AdminIdConverterFactory(VerifiableProperties verifiableProperties, MetricRegistry metricRegistry) {
    adminMetrics = new AdminMetrics(metricRegistry);
  }

  @Override
  public IdConverter getIdConverter() {
    return new AdminIdConverter(adminMetrics);
  }

  private static class AdminIdConverter implements IdConverter {
    private boolean isOpen = true;
    private final AdminMetrics adminMetrics;

    AdminIdConverter(AdminMetrics adminMetrics) {
      this.adminMetrics = adminMetrics;
    }

    @Override
    public void close() {
      isOpen = false;
    }

    /**
     * {@inheritDoc}
     * On any {@link RestMethod}, removes the leading slash in order to convert the path into an ID that the
     * {@link com.github.ambry.router.Router} will understand.
     * @param restRequest {@link RestRequest} representing the request.
     * @param input the ID that needs to be converted.
     * @param callback the {@link Callback} to invoke once the converted ID is available. Can be null.
     * @return a {@link Future} that will eventually contain the converted ID.
     */
    @Override
    public Future<String> convert(RestRequest restRequest, String input, Callback<String> callback) {
      FutureResult<String> futureResult = new FutureResult<String>();
      String convertedId = null;
      Exception exception = null;
      adminMetrics.idConverterRequestRate.mark();
      long startTimeInMs = System.currentTimeMillis();
      if (!isOpen) {
        exception = new RestServiceException("IdConverter is closed", RestServiceErrorCode.ServiceUnavailable);
      } else {
        convertedId = input.startsWith("/") ? input.substring(1) : input;
      }
      futureResult.done(convertedId, exception);
      if (callback != null) {
        callback.onCompletion(convertedId, exception);
      }
      adminMetrics.idConverterProcessingTimeInMs.update(System.currentTimeMillis() - startTimeInMs);
      return futureResult;
    }
  }
}
