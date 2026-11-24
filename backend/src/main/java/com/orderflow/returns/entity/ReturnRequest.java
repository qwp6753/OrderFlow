package com.orderflow.returns.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("return_request")
public class ReturnRequest {
  @TableId(type = IdType.AUTO)
  private Long id;
  private String returnNo;
  private Long orderId;
  private String status;
  private String reason;
  private Long createdBy;
  private LocalDateTime createdAt;
  private Long reviewedBy;
  private LocalDateTime reviewedAt;
  private String rejectionReason;
  private Long receivedBy;
  private LocalDateTime receivedAt;
  private String receiptRemark;
  private LocalDateTime updatedAt;
}
