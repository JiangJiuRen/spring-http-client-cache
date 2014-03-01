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
package cz.jirutka.spring.http.client.cache;

import cz.jirutka.spring.http.client.cache.internal.CacheControl;
import cz.jirutka.spring.http.client.cache.internal.CacheEntry;
import net.jcip.annotations.Immutable;
import org.springframework.http.HttpRequest;

import java.util.Date;

import static cz.jirutka.spring.http.client.cache.internal.CacheControl.parseCacheControl;

@Immutable
public class DefaultCachedEntrySuitabilityChecker implements CachedEntrySuitabilityChecker {

    public boolean canCachedEntryBeUsed(HttpRequest request, CacheEntry entry, Date now) {

        if (now.after(entry.getResponseExpiration())) {
            return false;
        }
        CacheControl cc = parseCacheControl(request.getHeaders());

        if (cc.getMaxAge() > -1 && responseCurrentAge(entry, now) > cc.getMaxAge()) {
            return false;
        }

        return true;
    }


    private long responseCurrentAge(CacheEntry entry, Date now) {
        return (now.getTime() - entry.getResponseCreated().getTime()) / 1000L;
    }
}
