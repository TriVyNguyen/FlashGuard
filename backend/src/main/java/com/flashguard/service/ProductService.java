package com.flashguard.service;

import com.flashguard.dto.CreateProductRequest;
import com.flashguard.dto.ProductResponse;
import com.flashguard.entity.Product;
import com.flashguard.exception.ApiException;
import com.flashguard.exception.ErrorCode;
import com.flashguard.repository.ProductRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest req) {
        Product p = new Product();
        p.setName(req.name().trim());
        p.setOriginalPrice(req.originalPrice());
        p.setStockQuantity(req.stockQuantity());
        return ProductResponse.from(productRepository.save(p));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return productRepository.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.PRODUCT_NOT_FOUND,
                        "Không tìm thấy sản phẩm #" + id));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        return productRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(ProductResponse::from)
                .toList();
    }
}
