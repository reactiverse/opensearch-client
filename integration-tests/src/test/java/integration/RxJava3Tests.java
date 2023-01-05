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

import io.reactiverse.opensearch.client.rxjava3.RestHighLevelClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rxjava3.core.Vertx;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ExtendWith(VertxExtension.class)
public class RxJava3Tests {

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
  void indexThenGet(Vertx vertx, VertxTestContext testContext) {
    String yo = "{\"foo\": \"bar\"}";
    IndexRequest req = new IndexRequest("posts").id("1").source(yo, XContentType.JSON);
    client
      .rxIndexAsync(req, RequestOptions.DEFAULT)
      .flatMap(resp -> client.rxGetAsync(new GetRequest("posts").id("1"), RequestOptions.DEFAULT))
      .subscribe(resp -> testContext.verify(() -> {
        assertThat(Thread.currentThread().getName()).startsWith("vert.x-eventloop-thread-");
        assertThat(vertx.getOrCreateContext().isEventLoopContext()).isTrue();
        assertThat(resp.getSourceAsMap()).hasEntrySatisfying("foo", "bar"::equals);
        testContext.completeNow();
      }), testContext::failNow);
  }
}
