package com.ecommerce.management.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.dto.cart.AddCartItemRequest;
import com.ecommerce.management.dto.cart.CartItemResponse;
import com.ecommerce.management.dto.cart.CartResponse;
import com.ecommerce.management.dto.cart.UpdateCartItemRequest;
import com.ecommerce.management.entity.AppUser;
import com.ecommerce.management.entity.Cart;
import com.ecommerce.management.entity.CartItem;
import com.ecommerce.management.entity.Product;
import com.ecommerce.management.entity.enums.RecordStatus;
import com.ecommerce.management.repository.AppUserRepository;
import com.ecommerce.management.repository.CartRepository;
import com.ecommerce.management.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartService {
    private final CartRepository cartRepository;
    private final AppUserRepository userRepository;
    private final ProductRepository productRepository;

    @Transactional
    public CartResponse getCart(Long userId) {
        return toResponse(getOrCreateCart(userId));
    }

    @Transactional
    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        Cart cart = getOrCreateCart(userId);
        Product product = getAvailableProduct(request.productId());
        CartItem existing = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst()
                .orElse(null);

        int currentQuantity = existing == null ? 0 : existing.getQuantity();
        int requestedQuantity;
        try {
            requestedQuantity = Math.addExact(currentQuantity, request.quantity());
        } catch (ArithmeticException exception) {
            throw quantityConflict(product);
        }
        validateQuantity(product, requestedQuantity);

        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            item.setQuantity(requestedQuantity);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            cart.getItems().add(item);
        } else {
            existing.setQuantity(requestedQuantity);
            existing.setUpdatedAt(now);
        }
        cart.setUpdatedAt(now);
        return toResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse updateItem(Long userId, Long productId, UpdateCartItemRequest request) {
        Cart cart = getExistingCart(userId);
        CartItem item = findItem(cart, productId);
        Product product = getAvailableProduct(productId);
        validateQuantity(product, request.quantity());

        LocalDateTime now = LocalDateTime.now();
        item.setQuantity(request.quantity());
        item.setUpdatedAt(now);
        cart.setUpdatedAt(now);
        return toResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse removeItem(Long userId, Long productId) {
        Cart cart = getExistingCart(userId);
        CartItem item = findItem(cart, productId);
        cart.getItems().remove(item);
        cart.setUpdatedAt(LocalDateTime.now());
        return toResponse(cartRepository.save(cart));
    }

    @Transactional
    public void clearCart(Long userId) {
        Cart cart = cartRepository.findByUserIdWithItems(userId).orElse(null);
        if (cart == null || cart.getItems().isEmpty()) return;
        cart.getItems().clear();
        cart.setUpdatedAt(LocalDateTime.now());
        cartRepository.save(cart);
    }

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserIdWithItems(userId)
                .orElseGet(() -> createCart(userId));
    }

    private Cart createCart(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Authenticated user no longer exists"));
        LocalDateTime now = LocalDateTime.now();
        Cart cart = new Cart();
        cart.setUser(user);
        cart.setCreatedAt(now);
        cart.setUpdatedAt(now);
        return cartRepository.save(cart);
    }

    private Cart getExistingCart(Long userId) {
        return cartRepository.findByUserIdWithItems(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart not found"));
    }

    private CartItem findItem(Cart cart, Long productId) {
        return cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Product is not in cart: " + productId));
    }

    private Product getAvailableProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Product not found: " + productId));
        if (product.getStatus() != RecordStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Product is not active: " + productId);
        }
        return product;
    }

    private void validateQuantity(Product product, int quantity) {
        if (quantity > 100 || quantity > product.getStock()) throw quantityConflict(product);
    }

    private ResponseStatusException quantityConflict(Product product) {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Requested quantity exceeds available stock for product: " + product.getId());
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream().map(item -> {
            Product product = item.getProduct();
            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            return new CartItemResponse(product.getId(), product.getName(), product.getSku(),
                    product.getCategory().getId(), product.getCategory().getName(), product.getPrice(),
                    item.getQuantity(), lineTotal, product.getStock());
        }).toList();
        int totalItems = items.stream().mapToInt(CartItemResponse::quantity).sum();
        BigDecimal subtotal = items.stream().map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(cart.getId(), items, totalItems, subtotal, "TRY", cart.getUpdatedAt());
    }
}
