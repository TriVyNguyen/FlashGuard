package com.flashguard.controller;

import com.flashguard.dto.CreateProductRequest;
import com.flashguard.dto.ProductResponse;
import com.flashguard.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @Operation(summary = "Tạo sản phẩm")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest req) {
        ProductResponse created = productService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/products/" + created.id()))
                .body(created);
    }

    @GetMapping
    @Operation(summary = "Danh sách sản phẩm")
    public List<ProductResponse> list() {
        return productService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem sản phẩm")
    public ProductResponse get(@PathVariable Long id) {
        return productService.get(id);
    }
}
