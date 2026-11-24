package com.orderflow.returns.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orderflow.orders.entity.OrderItem;
import com.orderflow.returns.entity.ReturnItem;
import java.util.List;
import org.apache.ibatis.annotations.Select;

public interface ReturnItemMapper extends BaseMapper<ReturnItem> {
  @Select("SELECT * FROM order_item WHERE id=#{id} FOR UPDATE")
  OrderItem lockOrderItem(Long id);

  @Select("""
      SELECT COALESCE(SUM(i.quantity),0)
      FROM return_item i JOIN return_request r ON r.id=i.return_id
      WHERE i.order_item_id=#{id} AND r.status <> 'REJECTED'
      """)
  long claimed(Long id);

  record ClaimedQuantity(Long orderItemId, long quantity) {}

  @Select("""
      SELECT i.order_item_id, SUM(i.quantity) AS quantity
      FROM order_item oi
      JOIN return_item i ON i.order_item_id=oi.id
      JOIN return_request r ON r.id=i.return_id
      WHERE oi.order_id=#{orderId} AND r.status <> 'REJECTED'
      GROUP BY i.order_item_id
      """)
  List<ClaimedQuantity> claimedForOrder(Long orderId);
}
