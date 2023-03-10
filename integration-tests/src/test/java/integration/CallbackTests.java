/*
 * Copyright 2018 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package integration;

import io.reactiverse.opensearch.client.RestHighLevelClient;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.admin.cluster.settings.ClusterGetSettingsRequest;
import org.opensearch.action.admin.cluster.settings.ClusterGetSettingsResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.rest.RestStatus;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ExtendWith(VertxExtension.class)
class CallbackTests {

  @Container
  private OpensearchContainer container = new OpensearchContainer(
      DockerImageName.parse("opensearchproject/opensearch:2.4.1").asCompatibleSubstituteFor("opensearchproject/opensearch")
    );

  private RestHighLevelClient client;

  @BeforeEach
  void prepare(Vertx vertx) {
    RestClientBuilder builder = RestClient.builder(
      new HttpHost(container.getContainerIpAddress(), container.getMappedPort(9200), "http"));
    client = RestHighLevelClient.create(vertx, builder);
  }

  @AfterEach
  void close() {
    client.close();
  }

  @Test
  void index(VertxTestContext testContext) {
    String yo = "{\"foo\": \"bar\"}";
    IndexRequest req = new IndexRequest("posts").source(yo, XContentType.JSON);
    client.indexAsync(req, RequestOptions.DEFAULT, ar -> {
      if (ar.succeeded()) {
        IndexResponse response = ar.result();
        testContext.verify(() -> {
          assertThat(response.status()).isEqualByComparingTo(RestStatus.CREATED);
          assertThat(response.getResult()).isEqualByComparingTo(DocWriteResponse.Result.CREATED);
          testContext.completeNow();
        });
      } else {
        testContext.failNow(ar.cause());
      }
    });
  }

  @Test
  void getClusterSettings(VertxTestContext testContext) {
    ClusterGetSettingsRequest req = new ClusterGetSettingsRequest();
    client.cluster().getSettingsAsync(req, RequestOptions.DEFAULT, ar -> {
      if (ar.succeeded()) {
        ClusterGetSettingsResponse response = ar.result();
        testContext.verify(() -> {
          assertThat(response).isNotNull();
          assertThat(response.getTransientSettings().isEmpty()).isTrue();
          assertThat(response.getPersistentSettings().isEmpty()).isTrue();
          testContext.completeNow();
        });
      } else {
        testContext.failNow(ar.cause());
      }
    });
  }

  @Test
  void checkThreadingModel(Vertx vertx, VertxTestContext testContext) {
    client.existsAsync(new GetRequest(), RequestOptions.DEFAULT, event -> {
      testContext.verify(() -> {
        assertThat(Thread.currentThread().getName()).startsWith("vert.x-eventloop-thread-");
        assertThat(vertx.getOrCreateContext().isEventLoopContext()).isTrue();
        testContext.completeNow();
      });
    });
  }
}
