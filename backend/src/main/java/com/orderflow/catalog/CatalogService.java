package com.orderflow.catalog;

import static com.orderflow.catalog.CatalogRequests.*;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orderflow.catalog.entity.*;
import com.orderflow.catalog.mapper.*;
import com.orderflow.common.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogService {
  private final ProductMapper products;
  private final SkuMapper skus;
  private final StockMapper stocks;

  public Page<Product> page(int page, int size, String name, String status) {
    return products.selectPage(
        new Page<>(page, size),
        Wrappers.<Product>lambdaQuery()
            .like(name != null && !name.isBlank(), Product::getName, name)
            .eq(status != null, Product::getStatus, status)
            .orderByDesc(Product::getId));
  }

  public Product detail(Long id) {
    var p = products.selectById(id);
    if (p == null) throw BusinessException.missing();
    return p;
  }

  @Transactional
  public Product create(ProductInput input) {
    var p = new Product();
    p.setName(input.name());
    p.setDescription(input.description());
    p.setStatus("OFF_SALE");
    products.insert(p);
    return p;
  }

  @Transactional
  public Product update(Long id, ProductInput input) {
    products.update(null, Wrappers.<Product>lambdaUpdate()
        .eq(Product::getId, id)
        .set(Product::getName, input.name())
        .set(Product::getDescription, input.description()));
    return detail(id);
  }

  @Transactional
  public Product status(Long id, String status) {
    products.update(null, Wrappers.<Product>lambdaUpdate()
        .eq(Product::getId, id)
        .set(Product::getStatus, status));
    return detail(id);
  }

  public record SkuView(
      Long id,
      Long productId,
      String code,
      String name,
      BigDecimal price,
      String status,
      int quantity,
      int reservedQuantity,
      int availableQuantity) {}

  public List<SkuView> listSkus(Long productId) {
    detail(productId);
    var list =
        skus.selectList(
            Wrappers.<Sku>lambdaQuery().eq(Sku::getProductId, productId).orderByAsc(Sku::getId));
    if (list.isEmpty()) return List.of();
    var stockMap =
        stocks
            .selectList(
                Wrappers.<Stock>lambdaQuery()
                    .in(Stock::getSkuId, list.stream().map(Sku::getId).toList()))
            .stream()
            .collect(java.util.stream.Collectors.toMap(Stock::getSkuId, s -> s));
    return list.stream()
        .map(
            s -> {
              var stock = stockMap.get(s.getId());
              return new SkuView(
                  s.getId(),
                  s.getProductId(),
                  s.getCode(),
                  s.getName(),
                  s.getPrice(),
                  s.getStatus(),
                  stock.getQuantity(),
                  stock.getReservedQuantity(),
                  stock.getQuantity() - stock.getReservedQuantity());
            })
        .toList();
  }

  @Transactional
  public Sku createSku(Long productId, SkuInput input) {
    detail(productId);
    var s = new Sku();
    s.setProductId(productId);
    apply(s, input);
    skus.insert(s);
    var stock = new Stock();
    stock.setSkuId(s.getId());
    stock.setQuantity(0);
    stock.setReservedQuantity(0);
    stocks.insert(stock);
    return s;
  }

  @Transactional
  public Sku updateSku(Long productId, Long skuId, SkuInput input) {
    var s = skus.selectById(skuId);
    if (s == null || !s.getProductId().equals(productId)) throw BusinessException.missing();
    apply(s, input);
    skus.updateById(s);
    return s;
  }

  private void apply(Sku s, SkuInput input) {
    s.setCode(input.code());
    s.setName(input.name());
    s.setPrice(input.price());
    s.setStatus(input.status());
  }
}
