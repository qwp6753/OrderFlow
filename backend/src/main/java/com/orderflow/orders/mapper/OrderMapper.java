package com.orderflow.orders.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orderflow.orders.entity.SalesOrder;
import org.apache.ibatis.annotations.*;

public interface OrderMapper extends BaseMapper<SalesOrder> {
  @Update(
      "UPDATE sales_order SET status='PENDING_SHIPMENT' WHERE id=#{id} AND"
          + " status='PENDING_PAYMENT'")
  int pay(Long id);

  @Update(
      "UPDATE sales_order SET status='CANCELED' WHERE id=#{id} AND status IN"
          + " ('PENDING_PAYMENT','PENDING_SHIPMENT')")
  int cancel(Long id);

  @Update("UPDATE sales_order SET status='SHIPPED' WHERE id=#{id} AND status='PENDING_SHIPMENT'")
  int ship(Long id);

  @Update(
      "UPDATE sales_order SET status='COMPLETED',completed_at=#{time} WHERE id=#{id} AND"
          + " status='SHIPPED'")
  int complete(@Param("id") Long id, @Param("time") java.time.LocalDateTime time);
}
