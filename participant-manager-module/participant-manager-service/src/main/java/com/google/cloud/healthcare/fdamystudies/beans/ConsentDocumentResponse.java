package com.google.cloud.healthcare.fdamystudies.beans;

import com.google.cloud.healthcare.fdamystudies.common.ErrorCode;
import com.google.cloud.healthcare.fdamystudies.common.MessageCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ConsentDocumentResponse extends BaseResponse {
  private String version;
  private String type;
  private String content;

  public ConsentDocumentResponse(ErrorCode errorCode) {
    super(errorCode);
  }

  public ConsentDocumentResponse(MessageCode messageCode) {
    super(messageCode);
  }
  
  public ConsentDocumentResponse(
      MessageCode messageCode, String version, String type, String content) {
    super(messageCode);
    this.version = version;
    this.type = type;
    this.content = content;
  }
}
