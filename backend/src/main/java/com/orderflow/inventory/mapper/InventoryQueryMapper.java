package com.orderflow.inventory.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orderflow.inventory.InventoryViews.*;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.*;

public interface InventoryQueryMapper {
  @Select(
      """
      <script>
      SELECT s.id AS sku_id,p.name AS product_name,s.code AS sku_code,s.name AS sku_name,s.status AS sku_status,
      st.quantity,st.reserved_quantity,st.quantity-st.reserved_quantity AS available_quantity
      FROM stock st JOIN sku s ON s.id=st.sku_id JOIN product p ON p.id=s.product_id
      <where><if test="keyword != null and keyword != ''">(s.code LIKE CONCAT('%',#{keyword},'%') OR s.name LIKE CONCAT('%',#{keyword},'%') OR p.name LIKE CONCAT('%',#{keyword},'%'))</if></where>
      ORDER BY s.id DESC
      </script>
      """)
  Page<StockRow> stocks(Page<StockRow> page, @Param("keyword") String keyword);

  @Select(
      """
      <script>
      SELECT m.id,m.sku_id,s.code AS sku_code,m.order_id,o.order_no,m.movement_type,m.quantity_delta,m.reserved_delta,
      m.quantity_after,m.reserved_after,m.remark,u.display_name AS operator_name,m.created_at
      FROM stock_movement m JOIN sku s ON s.id=m.sku_id JOIN sys_user u ON u.id=m.operator_id LEFT JOIN sales_order o ON o.id=m.order_id
      <where>
      <if test="skuId != null">AND m.sku_id=#{skuId}</if>
      <if test="skuCode != null and skuCode != ''">AND s.code=#{skuCode}</if>
      <if test="type != null">AND m.movement_type=#{type}</if>
      <if test="from != null">AND m.created_at &gt;=#{from}</if>
      <if test="to != null">AND m.created_at &lt;=#{to}</if>
      </where> ORDER BY m.id DESC
      </script>
      """)
  Page<MovementRow> movements(
      Page<MovementRow> page,
      @Param("skuId") Long skuId,
      @Param("skuCode") String skuCode,
      @Param("type") String type,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);
}
