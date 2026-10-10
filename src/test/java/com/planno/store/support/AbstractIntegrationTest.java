package com.planno.store.support;

import com.planno.store.cart.repository.CartItemRepository;
import com.planno.store.cart.repository.CartRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planno.store.catalog.repository.CategoryRepository;
import com.planno.store.catalog.repository.ProductRepository;
import com.planno.store.core.security.AuthenticatedUser;
import com.planno.store.core.security.JwtService;
import com.planno.store.identity.domain.Role;
import com.planno.store.identity.domain.RoleName;
import com.planno.store.identity.domain.User;
import com.planno.store.identity.domain.UserRoleAssignment;
import com.planno.store.identity.repository.RoleRepository;
import com.planno.store.identity.repository.UserRepository;
import com.planno.store.identity.repository.UserRoleAssignmentRepository;
import com.planno.store.identity.service.ApplicationUserDetailsService;
import com.planno.store.inventory.repository.InventoryItemRepository;
import com.planno.store.order.repository.OrderRepository;
import com.planno.store.order.repository.OrderItemRepository;
import com.planno.store.order.repository.OrderStatusHistoryRepository;
import com.planno.store.payment.account.repository.PaymentProviderAccountRepository;
import com.planno.store.payment.account.repository.PaymentProviderOAuthStateRepository;
import com.planno.store.payment.refund.repository.RefundRepository;
import com.planno.store.payment.repository.PaymentRepository;
import com.planno.store.payment.repository.WebhookEventRepository;
import com.planno.store.pricing.coupon.repository.CouponRepository;
import com.planno.store.pricing.promotion.repository.PromotionRepository;
import com.planno.store.shipping.repository.AddressRepository;
import com.planno.store.shipping.repository.ShippingMethodRepository;
import com.planno.store.store.domain.Store;
import com.planno.store.store.repository.StoreRepository;
import com.planno.store.store.provisioning.StoreProvisioningRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String STORE_HEADER = "X-Store-Slug";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected StoreRepository storeRepository;

    @Autowired
    protected StoreProvisioningRepository storeProvisioningRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected UserRoleAssignmentRepository assignmentRepository;

    @Autowired
    protected CategoryRepository categoryRepository;

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected InventoryItemRepository inventoryItemRepository;

    @Autowired
    protected CartRepository cartRepository;

    @Autowired
    protected CartItemRepository cartItemRepository;

    @Autowired
    protected ShippingMethodRepository shippingMethodRepository;

    @Autowired
    protected AddressRepository addressRepository;

    @Autowired
    protected OrderRepository orderRepository;

    @Autowired
    protected OrderItemRepository orderItemRepository;

    @Autowired
    protected OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    protected PaymentProviderOAuthStateRepository paymentProviderOAuthStateRepository;

    @Autowired
    protected PaymentProviderAccountRepository paymentProviderAccountRepository;

    @Autowired
    protected PaymentRepository paymentRepository;

    @Autowired
    protected WebhookEventRepository webhookEventRepository;

    @Autowired
    protected RefundRepository refundRepository;

    @Autowired
    protected PromotionRepository promotionRepository;

    @Autowired
    protected CouponRepository couponRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected ApplicationUserDetailsService applicationUserDetailsService;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        webhookEventRepository.deleteAllInBatch();
        refundRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        promotionRepository.deleteAllInBatch();
        paymentProviderOAuthStateRepository.deleteAllInBatch();
        paymentProviderAccountRepository.deleteAllInBatch();
        orderStatusHistoryRepository.deleteAllInBatch();
        addressRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        shippingMethodRepository.deleteAllInBatch();
        cartItemRepository.deleteAllInBatch();
        cartRepository.deleteAllInBatch();
        inventoryItemRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        assignmentRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        roleRepository.deleteAllInBatch();
        storeProvisioningRepository.deleteAllInBatch();
        storeRepository.deleteAllInBatch();
    }

    protected Store createStore(String slug) {
        return storeRepository.saveAndFlush(Store.create(
                "Store " + slug,
                slug,
                slug + "@example.com",
                "BRL",
                "America/Sao_Paulo",
                "Store " + slug,
                null,
                null,
                null,
                null,
                null
        ));
    }

    protected Role ensureRole(RoleName roleName) {
        return roleRepository.findByName(roleName).orElseGet(() -> roleRepository.saveAndFlush(new Role(roleName)));
    }

    protected User createUser(String email, String password) {
        return userRepository.saveAndFlush(User.create(
                email,
                passwordEncoder.encode(password),
                "Test",
                "User"
        ));
    }

    protected void assignRole(User user, RoleName roleName, Store store) {
        assignmentRepository.saveAndFlush(new UserRoleAssignment(user, ensureRole(roleName), store));
    }

    protected String tokenFor(String email) {
        AuthenticatedUser user = (AuthenticatedUser) applicationUserDetailsService.loadUserByUsername(email);
        return jwtService.generateToken(user);
    }
}
