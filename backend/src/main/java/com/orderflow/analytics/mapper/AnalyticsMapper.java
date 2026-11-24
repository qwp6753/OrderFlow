package com.orderflow.analytics.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orderflow.analytics.AnalyticsViews.*;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AnalyticsMapper {
  String SALES_EVENTS = """
      SELECT i.sku_id, i.id AS item_id, i.quantity AS sold, 0 AS returned,
             i.unit_price*i.quantity AS amount
      FROM order_item i JOIN sales_order o ON o.id=i.order_id
      WHERE o.status='COMPLETED' AND o.completed_at >= #{start} AND o.completed_at < #{end}
      UNION ALL
      SELECT i.sku_id, i.id, 0, ri.quantity, 0
      FROM return_request r JOIN return_item ri ON ri.return_id=r.id
      JOIN order_item i ON i.id=ri.order_item_id
      WHERE r.status='COMPLETED' AND r.received_at >= #{start} AND r.received_at < #{end}
      """;

  @Select("""
      SELECT DATE(created_at) AS bucket_date, status, COUNT(*) AS amount
      FROM sales_order WHERE created_at >= #{start} AND created_at < #{end}
      GROUP BY DATE(created_at), status
      """)
  List<CreatedDay> createdOrders(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  @Select("""
      SELECT DATE(completed_at) AS bucket_date, COUNT(*) AS amount, SUM(total_amount) AS revenue
      FROM sales_order WHERE status='COMPLETED' AND completed_at >= #{start} AND completed_at < #{end}
      GROUP BY DATE(completed_at)
      """)
  List<CompletedDay> completedOrders(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  @Select("""
      SELECT status, COUNT(*) AS amount FROM return_request
      WHERE created_at >= #{start} AND created_at < #{end} GROUP BY status
      """)
  List<StatusCount> createdReturns(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  @Select("""
      SELECT COUNT(DISTINCT r.id) AS cases, COALESCE(SUM(i.quantity),0) AS units,
             COALESCE(SUM(o.unit_price*i.quantity),0) AS amount
      FROM return_request r
      LEFT JOIN return_item i ON i.return_id=r.id
      LEFT JOIN order_item o ON o.id=i.order_item_id
      WHERE r.status='COMPLETED' AND r.received_at >= #{start} AND r.received_at < #{end}
      """)
  Returns completedReturns(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  @Select("""
      SELECT a.sku_id, i.product_name, i.sku_code, a.sold, a.returned,
             a.sold-a.returned AS net_sold, a.amount
      FROM (
        SELECT sku_id, MAX(item_id) AS label_item, SUM(sold) AS sold,
               SUM(returned) AS returned, SUM(amount) AS amount
        FROM (
      """ + SALES_EVENTS + """
        ) events GROUP BY sku_id
      ) a JOIN order_item i ON i.id=a.label_item
      ORDER BY a.sold DESC, a.sku_id ASC LIMIT 10
      """)
  List<TopSku> topSkus(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  @Select("""
      SELECT a.product_id, i.product_name, a.sold, a.returned,
             a.sold-a.returned AS net_sold, a.amount
      FROM (
        SELECT s.product_id, MAX(e.item_id) AS label_item, SUM(e.sold) AS sold,
               SUM(e.returned) AS returned, SUM(e.amount) AS amount
        FROM (
      """ + SALES_EVENTS + """
        ) e JOIN sku s ON s.id=e.sku_id
        GROUP BY s.product_id
      ) a JOIN order_item i ON i.id=a.label_item
      ORDER BY a.sold DESC, a.product_id ASC LIMIT 10
      """)
  List<TopProduct> topProducts(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  @Select("""
      SELECT (SELECT COUNT(*) FROM sku) AS sku_count, COALESCE(SUM(quantity),0) AS physical,
             COALESCE(SUM(reserved_quantity),0) AS reserved,
             COALESCE(SUM(quantity-reserved_quantity),0) AS available,
             COUNT(CASE WHEN quantity-reserved_quantity < #{threshold} THEN 1 END) AS low_count
      FROM stock
      """)
  Stock inventory(@Param("threshold") int threshold);

  @Select("""
      SELECT s.id AS sku_id, p.name AS product_name, s.code AS sku_code,
             st.quantity AS physical, st.reserved_quantity AS reserved,
             st.quantity-st.reserved_quantity AS available
      FROM stock st JOIN sku s ON s.id=st.sku_id JOIN product p ON p.id=s.product_id
      WHERE st.quantity-st.reserved_quantity < #{threshold}
      ORDER BY available ASC, s.id ASC
      """)
  Page<LowSku> lowStock(Page<LowSku> page, @Param("threshold") int threshold);
}
