/*
 * Copyright 2020 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */
package com.google.cloud.healthcare.fdamystudies.controller;

import com.google.cloud.healthcare.fdamystudies.beans.ConsentDocument;
import com.google.cloud.healthcare.fdamystudies.service.ConsentService;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import static com.google.cloud.healthcare.fdamystudies.common.CommonConstants.USER_ID_HEADER;

@RestController
public class ConsentController {

  private static final String STATUS_LOG = "status=%d ";

  private static final String BEGIN_REQUEST_LOG = "%s request";

  private XLogger logger = XLoggerFactory.getXLogger(ConsentController.class.getName());

  @Autowired private ConsentService consentService;

  @GetMapping("/consents/{consentId}/consentDocument")
  public ResponseEntity<ConsentDocument> getConsentDocument(
      @PathVariable String consentId,
      @RequestHeader(name = USER_ID_HEADER) String userId,
      HttpServletRequest request) {
    logger.entry(BEGIN_REQUEST_LOG, request.getRequestURI());
    ConsentDocument consentDocument = consentService.getConsentDocument(consentId, userId);

    logger.exit(String.format(STATUS_LOG, consentDocument.getHttpStatusCode()));
    return ResponseEntity.status(consentDocument.getHttpStatusCode()).body(consentDocument);
  }
}
