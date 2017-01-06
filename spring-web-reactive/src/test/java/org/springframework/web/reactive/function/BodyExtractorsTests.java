/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.reactive.function;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonView;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.core.codec.ByteBufferDecoder;
import org.springframework.core.codec.StringDecoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.UnsupportedMediaTypeException;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.xml.Jaxb2XmlDecoder;
import org.springframework.mock.http.server.reactive.test.MockServerHttpRequest;

import static org.springframework.http.codec.json.AbstractJackson2Codec.JSON_VIEW_HINT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Arjen Poutsma
 * @author Sebastien Deleuze
 */
public class BodyExtractorsTests {

	private BodyExtractor.Context context;

	private Map<String, Object> hints;

	@Before
	public void createContext() {
		final List<HttpMessageReader<?>> messageReaders = new ArrayList<>();
		messageReaders.add(new DecoderHttpMessageReader<>(new ByteBufferDecoder()));
		messageReaders.add(new DecoderHttpMessageReader<>(new StringDecoder()));
		messageReaders.add(new DecoderHttpMessageReader<>(new Jaxb2XmlDecoder()));
		messageReaders.add(new DecoderHttpMessageReader<>(new Jackson2JsonDecoder()));

		this.context = new BodyExtractor.Context() {
			@Override
			public Supplier<Stream<HttpMessageReader<?>>> messageReaders() {
				return messageReaders::stream;
			}
			@Override
			public Map<String, Object> hints() {
				return hints;
			}
		};
		this.hints = new HashMap();
	}

	@Test
	public void toMono() throws Exception {
		BodyExtractor<Mono<String>, ReactiveHttpInputMessage> extractor = BodyExtractors.toMono(String.class);

		DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
		DefaultDataBuffer dataBuffer =
				factory.wrap(ByteBuffer.wrap("foo".getBytes(StandardCharsets.UTF_8)));
		Flux<DataBuffer> body = Flux.just(dataBuffer);

		MockServerHttpRequest request = new MockServerHttpRequest();
		request.setBody(body);

		Mono<String> result = extractor.extract(request, this.context);

		StepVerifier.create(result)
				.expectNext("foo")
				.expectComplete()
				.verify();
	}

	@Test
	public void toMonoWithHints() throws Exception {
		BodyExtractor<Mono<User>, ReactiveHttpInputMessage> extractor = BodyExtractors.toMono(User.class);
		this.hints.put(JSON_VIEW_HINT, SafeToDeserialize.class);

		DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
		DefaultDataBuffer dataBuffer =
				factory.wrap(ByteBuffer.wrap("{\"username\":\"foo\",\"password\":\"bar\"}".getBytes(StandardCharsets.UTF_8)));
		Flux<DataBuffer> body = Flux.just(dataBuffer);

		MockServerHttpRequest request = new MockServerHttpRequest();
		request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		request.setBody(body);

		Mono<User> result = extractor.extract(request, this.context);

		StepVerifier.create(result)
				.consumeNextWith(user -> {
					assertEquals("foo", user.getUsername());
					assertNull(user.getPassword());
				})
				.expectComplete()
				.verify();
	}

	@Test
	public void toFlux() throws Exception {
		BodyExtractor<Flux<String>, ReactiveHttpInputMessage> extractor = BodyExtractors.toFlux(String.class);

		DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
		DefaultDataBuffer dataBuffer =
				factory.wrap(ByteBuffer.wrap("foo".getBytes(StandardCharsets.UTF_8)));
		Flux<DataBuffer> body = Flux.just(dataBuffer);

		MockServerHttpRequest request = new MockServerHttpRequest();
		request.setBody(body);

		Flux<String> result = extractor.extract(request, this.context);

		StepVerifier.create(result)
				.expectNext("foo")
				.expectComplete()
				.verify();
	}

	@Test
	public void toFluxWithHints() throws Exception {
		BodyExtractor<Flux<User>, ReactiveHttpInputMessage> extractor = BodyExtractors.toFlux(User.class);
		this.hints.put(JSON_VIEW_HINT, SafeToDeserialize.class);

		DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
		DefaultDataBuffer dataBuffer =
				factory.wrap(ByteBuffer.wrap("[{\"username\":\"foo\",\"password\":\"bar\"},{\"username\":\"bar\",\"password\":\"baz\"}]".getBytes(StandardCharsets.UTF_8)));
		Flux<DataBuffer> body = Flux.just(dataBuffer);

		MockServerHttpRequest request = new MockServerHttpRequest();
		request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		request.setBody(body);

		Flux<User> result = extractor.extract(request, this.context);

		StepVerifier.create(result)
				.consumeNextWith(user -> {
					assertEquals("foo", user.getUsername());
					assertNull(user.getPassword());
				})
				.consumeNextWith(user -> {
					assertEquals("bar", user.getUsername());
					assertNull(user.getPassword());
				})
				.expectComplete()
				.verify();
	}

	@Test
	public void toFluxUnacceptable() throws Exception {
		BodyExtractor<Flux<String>, ReactiveHttpInputMessage> extractor = BodyExtractors.toFlux(String.class);

		DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
		DefaultDataBuffer dataBuffer =
				factory.wrap(ByteBuffer.wrap("foo".getBytes(StandardCharsets.UTF_8)));
		Flux<DataBuffer> body = Flux.just(dataBuffer);

		MockServerHttpRequest request = new MockServerHttpRequest();
		request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		request.setBody(body);

		BodyExtractor.Context emptyContext = new BodyExtractor.Context() {
			@Override
			public Supplier<Stream<HttpMessageReader<?>>> messageReaders() {
				return Stream::empty;
			}
			@Override
			public Map<String, Object> hints() {
				return Collections.emptyMap();
			}
		};

		Flux<String> result = extractor.extract(request, emptyContext);
		StepVerifier.create(result)
				.expectError(UnsupportedMediaTypeException.class)
				.verify();
	}

	@Test
	public void toDataBuffers() throws Exception {
		BodyExtractor<Flux<DataBuffer>, ReactiveHttpInputMessage> extractor = BodyExtractors.toDataBuffers();

		DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
		DefaultDataBuffer dataBuffer =
				factory.wrap(ByteBuffer.wrap("foo".getBytes(StandardCharsets.UTF_8)));
		Flux<DataBuffer> body = Flux.just(dataBuffer);

		MockServerHttpRequest request = new MockServerHttpRequest();
		request.setBody(body);

		Flux<DataBuffer> result = extractor.extract(request, this.context);

		StepVerifier.create(result)
				.expectNext(dataBuffer)
				.expectComplete()
				.verify();
	}


	interface SafeToDeserialize {}

	private static class User {

		@JsonView(SafeToDeserialize.class)
		private String username;

		private String password;

		public User() {
		}

		public User(String username, String password) {
			this.username = username;
			this.password = password;
		}

		public String getUsername() {
			return username;
		}

		public void setUsername(String username) {
			this.username = username;
		}

		public String getPassword() {
			return password;
		}

		public void setPassword(String password) {
			this.password = password;
		}
	}

}