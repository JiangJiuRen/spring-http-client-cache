/*
 * Copyright 2014 Jakub Jirutka <jakub@jirutka.cz>.
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
package cz.jirutka.spring.http.client.cache

import cz.jirutka.spring.http.client.cache.internal.CacheEntry
import cz.jirutka.spring.http.client.cache.internal.HttpResponseCache
import cz.jirutka.spring.http.client.cache.internal.HttpResponseCacheImpl
import cz.jirutka.spring.http.client.cache.internal.InMemoryClientHttpResponse
import cz.jirutka.spring.http.client.cache.test.AbbreviatedTimeCategory
import cz.jirutka.spring.http.client.cache.test.HttpHeadersHelper
import org.springframework.cache.Cache
import org.springframework.http.HttpHeaders
import org.springframework.http.client.ClientHttpRequestExecution
import spock.lang.Specification
import spock.util.mop.Use

import static org.springframework.http.HttpStatus.OK

@Mixin(HttpHeadersHelper)
@Use(AbbreviatedTimeCategory)
class CachingHttpRequestInterceptorTest extends Specification {

    def cache = Mock(HttpResponseCache)
    def cachingPolicy = Mock(CachingPolicy)
    def suitabilityChecker = Mock(CachedEntrySuitabilityChecker)
    def execution = Mock(ClientHttpRequestExecution)

    def interceptor = new CachingHttpRequestInterceptor(cache, cachingPolicy, suitabilityChecker)
    def cacheEntry = new CacheEntry(new InMemoryClientHttpResponse(SOME_BODY, OK, new HttpHeaders()), now -1.min, now +2.min)


    def 'construct with defaults'() {
        setup:
            def cacheStore = Mock(Cache)
        when:
            def obj = new CachingHttpRequestInterceptor(cacheStore, true, 32)
        then:
            obj.cache instanceof HttpResponseCacheImpl
            obj.cachingPolicy instanceof DefaultCachingPolicy
            obj.cachedChecker instanceof DefaultCachedEntrySuitabilityChecker
    }

    def 'request is not servable from cache'() {
        setup:
            0 * cache._
        when:
            def returned = interceptor.intercept(request, EMPTY_BODY, execution)
        then:
            1 * cachingPolicy.isServableFromCache(request) >> false
            1 * execution.execute(request, EMPTY_BODY) >> response
        and:
            returned == response
    }

    def 'request is servable from cache, but not cached yet'() {
        when:
           def returned = interceptor.intercept(request, EMPTY_BODY, execution)
        then:
            1 * cachingPolicy.isServableFromCache(request) >> true
            1 * cache.getCacheEntry(request) >> null
        and:
            1 * execution.execute(request, EMPTY_BODY) >> response
        and:
            returned == response
    }

    def 'request is cached, but cannot be used here'() {
        when:
            def returned = interceptor.intercept(request, EMPTY_BODY, execution)
        then:
            1 * cachingPolicy.isServableFromCache(request) >> true
            1 * cache.getCacheEntry(request) >> cacheEntry
            1 * suitabilityChecker.canCachedEntryBeUsed(request, cacheEntry, _) >> false
        and:
            1 * execution.execute(request, EMPTY_BODY) >> response
        and:
            returned == response
    }

    def 'request is served from cache'() {
        setup:
            def expected = new InMemoryClientHttpResponse(SOME_BODY, OK, header(Age: 60))
            0 * execution._
        when:
            def returned = interceptor.intercept(request, EMPTY_BODY, execution)
        then:
            1 * cachingPolicy.isServableFromCache(request) >> true
            1 * cache.getCacheEntry(request) >> cacheEntry
            1 * suitabilityChecker.canCachedEntryBeUsed(request, cacheEntry, _) >> true
        and:
            returned == expected
    }

    def 'response is cacheable'() {
        setup:
            cachingPolicy.isServableFromCache(_) >> false
        when:
            def returned = interceptor.intercept(request, EMPTY_BODY, execution)
        then:
            1 * execution.execute(request, EMPTY_BODY) >> response
            1 * cachingPolicy.isResponseCacheable(request, response) >> true
            1 * cache.cacheAndReturnResponse(request, response, _ as Date, _ as Date) >> response
        and:
            returned == response
    }


    def header(kwargs = [:]) {
        def headers = new HttpHeaders()
        kwargs.each { key, val ->
            headers.add(key, val.toString())
        }
        headers
    }
}
