package com.itx.similarproducts.domain.port;

import java.util.List;

public interface SimilarProductIdsPort {

    List<String> findSimilarIds(String productId);
}
