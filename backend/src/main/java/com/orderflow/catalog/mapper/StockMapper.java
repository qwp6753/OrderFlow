package com.orderflow.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orderflow.catalog.entity.Stock;
import org.apache.ibatis.annotations.*;

public interface StockMapper extends BaseMapper<Stock> {
  @Update(
      "UPDATE stock SET reserved_quantity=reserved_quantity+#{quantity} WHERE sku_id=#{skuId} AND"
          + " quantity-reserved_quantity>=#{quantity}")
  int reserve(@Param("skuId") Long skuId, @Param("quantity") int quantity);

  @Update(
      "UPDATE stock SET reserved_quantity=reserved_quantity-#{quantity} WHERE sku_id=#{skuId} AND"
          + " reserved_quantity>=#{quantity}")
  int release(@Param("skuId") Long skuId, @Param("quantity") int quantity);

  @Select("SELECT id,sku_id,quantity,reserved_quantity FROM stock WHERE sku_id=#{skuId} FOR UPDATE")
  Stock locked(Long skuId);

  @Update(
      "UPDATE stock SET quantity=quantity+#{quantity} WHERE sku_id=#{skuId} AND"
          + " quantity<=2147483647-#{quantity}")
  int receive(@Param("skuId") Long skuId, @Param("quantity") int quantity);

  @Update(
      "UPDATE stock SET"
          + " quantity=quantity-#{quantity},reserved_quantity=reserved_quantity-#{quantity} WHERE"
          + " sku_id=#{skuId} AND reserved_quantity>=#{quantity}")
  int ship(@Param("skuId") Long skuId, @Param("quantity") int quantity);
}
