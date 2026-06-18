package com.merchanthub.integration;

import com.merchanthub.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

/** Thin client over the (mock) external shop API used by the pull-sync job. */
@Component
public class ShopApiClient {

    private final RestClient client;

    public ShopApiClient(RestClient.Builder builder, AppProperties props) {
        this.client = builder.baseUrl(props.getShopApiBaseUrl()).build();
    }

    public List<ShopDtos.ShopProduct> fetchProducts(String apiKey) {
        ShopDtos.ShopProductsResponse res = client.get()
                .uri("/shop/products")
                .header("X-Api-Key", apiKey)
                .retrieve()
                .body(ShopDtos.ShopProductsResponse.class);
        return res == null || res.products() == null ? List.of() : res.products();
    }

    public List<ShopDtos.ShopOrder> fetchOrders(String apiKey, Instant since) {
        ShopDtos.ShopOrdersResponse res = client.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/shop/orders");
                    if (since != null) uriBuilder.queryParam("since", since.toString());
                    return uriBuilder.build();
                })
                .header("X-Api-Key", apiKey)
                .retrieve()
                .body(ShopDtos.ShopOrdersResponse.class);
        return res == null || res.orders() == null ? List.of() : res.orders();
    }
}
