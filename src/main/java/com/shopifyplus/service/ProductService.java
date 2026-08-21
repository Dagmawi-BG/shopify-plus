package com.shopifyplus.service;

import com.shopifyplus.dto.ProductRequest;
import com.shopifyplus.dto.ProductUpdateRequest;
import com.shopifyplus.exception.NotFoundException;
import com.shopifyplus.model.Product;
import com.shopifyplus.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository repository;
    private final MongoTemplate mongo;

    public ProductService(ProductRepository repository, MongoTemplate mongo) {
        this.repository = repository;
        this.mongo = mongo;
    }

    public Product create(ProductRequest req) {
        Product product = Product.builder()
                .name(req.name())
                .price(req.price())
                .category(req.category())
                .stock(req.stock() == null ? 0 : req.stock())
                .description(req.description())
                .build();
        return repository.save(product);
    }

    public Page<Product> list(String category, String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return textSearch(search.trim(), category, pageable);
        }
        if (category != null && !category.isBlank()) {
            return repository.findByCategory(category, pageable);
        }
        return repository.findAll(pageable);
    }

    // Full-text search over the name/description text index, ranked by relevance score,
    // optionally narrowed to a category.
    private Page<Product> textSearch(String search, String category, Pageable pageable) {
        TextCriteria text = TextCriteria.forDefaultLanguage().matchingAny(search.split("\\s+"));

        TextQuery pageQuery = TextQuery.queryText(text).sortByScore();
        TextQuery countQuery = TextQuery.queryText(text);
        if (category != null && !category.isBlank()) {
            Criteria cat = Criteria.where("category").is(category);
            pageQuery.addCriteria(cat);
            countQuery.addCriteria(cat);
        }

        List<Product> content = mongo.find(pageQuery.with(pageable), Product.class);
        long total = mongo.count(countQuery, Product.class);
        return new PageImpl<>(content, pageable, total);
    }

    public Product getById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }

    public Product update(String id, ProductUpdateRequest req) {
        Product product = getById(id);
        if (req.name() != null) product.setName(req.name());
        if (req.price() != null) product.setPrice(req.price());
        if (req.category() != null) product.setCategory(req.category());
        if (req.stock() != null) product.setStock(req.stock());
        if (req.description() != null) product.setDescription(req.description());
        return repository.save(product);
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Product not found");
        }
        repository.deleteById(id);
    }
}
