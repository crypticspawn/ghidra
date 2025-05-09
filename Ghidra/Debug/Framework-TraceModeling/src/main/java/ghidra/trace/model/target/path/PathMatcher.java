/* ###
 * IP: GHIDRA
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
package ghidra.trace.model.target.path;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

public class PathMatcher implements PathFilter {
	protected static final Set<String> WILD_SINGLETON = Set.of("");

	protected final Set<PathPattern> patterns;

	public static PathMatcher any(Stream<PathPattern> patterns) {
		return new PathMatcher(patterns.collect(Collectors.toUnmodifiableSet()));
	}

	public static PathMatcher any(Collection<PathFilter> filters) {
		return any(filters.stream().flatMap(f -> f.getPatterns().stream()));
	}

	public static PathMatcher any(PathFilter... filters) {
		return any(Stream.of(filters).flatMap(f -> f.getPatterns().stream()));
	}

	PathMatcher(Set<PathPattern> patterns) {
		this.patterns = patterns;
	}

	@Override
	public String toString() {
		return String.format("<PathMatcher\n  %s\n>", StringUtils.join(patterns, "\n  "));
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof PathMatcher)) {
			return false;
		}
		PathMatcher that = (PathMatcher) obj;
		if (!Objects.equals(this.patterns, that.patterns)) {
			return false;
		}
		return true;
	}

	@Override
	public PathFilter or(PathFilter that) {
		Set<PathPattern> patterns = new HashSet<>();
		patterns.addAll(this.patterns);
		patterns.addAll(that.getPatterns());
		return new PathMatcher(Collections.unmodifiableSet(patterns));
	}

	/**
	 * TODO: We could probably do a lot better, esp. for many patterns, by using a trie.
	 */
	protected boolean anyPattern(Predicate<PathPattern> pred) {
		for (PathPattern p : patterns) {
			if (pred.test(p)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean matches(KeyPath path) {
		return anyPattern(p -> p.matches(path));
	}

	@Override
	public boolean successorCouldMatch(KeyPath path, boolean strict) {
		return anyPattern(p -> p.successorCouldMatch(path, strict));
	}

	@Override
	public boolean ancestorMatches(KeyPath path, boolean strict) {
		return anyPattern(p -> p.ancestorMatches(path, strict));
	}

	@Override
	public boolean ancestorCouldMatchRight(KeyPath path, boolean strict) {
		return anyPattern(p -> p.ancestorCouldMatchRight(path, strict));
	}

	@Override
	public KeyPath getSingletonPath() {
		if (patterns.size() != 1) {
			return null;
		}
		return patterns.iterator().next().getSingletonPath();
	}

	@Override
	public PathPattern getSingletonPattern() {
		if (patterns.size() != 1) {
			return null;
		}
		return patterns.iterator().next();
	}

	@Override
	public Set<PathPattern> getPatterns() {
		return patterns;
	}

	protected void coalesceWilds(Set<String> result) {
		if (result.contains("")) {
			result.removeIf(KeyPath::isName);
			result.add("");
		}
		if (result.contains("[]")) {
			result.removeIf(KeyPath::isIndex);
			result.add("[]");
		}
	}

	@Override
	public Set<String> getNextKeys(KeyPath path) {
		Set<String> result = new HashSet<>();
		for (PathPattern pattern : patterns) {
			result.addAll(pattern.getNextKeys(path));
		}
		coalesceWilds(result);
		return result;
	}

	@Override
	public Set<String> getNextNames(KeyPath path) {
		Set<String> result = new HashSet<>();
		for (PathPattern pattern : patterns) {
			result.addAll(pattern.getNextNames(path));
			if (result.contains("")) {
				return WILD_SINGLETON;
			}
		}
		return result;
	}

	@Override
	public Set<String> getNextIndices(KeyPath path) {
		Set<String> result = new HashSet<>();
		for (PathPattern pattern : patterns) {
			result.addAll(pattern.getNextIndices(path));
			if (result.contains("")) {
				return WILD_SINGLETON;
			}
		}
		return result;
	}

	@Override
	public Set<String> getPrevKeys(KeyPath path) {
		Set<String> result = new HashSet<>();
		for (PathPattern pattern : patterns) {
			result.addAll(pattern.getPrevKeys(path));
		}
		coalesceWilds(result);
		return result;
	}

	@Override
	public boolean isNone() {
		return patterns.isEmpty();
	}

	@Override
	public PathMatcher applyKeys(Align align, List<String> indices) {
		Set<PathPattern> patterns = new HashSet<>();
		for (PathPattern pat : this.patterns) {
			patterns.add(pat.applyKeys(align, indices));
		}
		return new PathMatcher(Collections.unmodifiableSet(patterns));
	}

	@Override
	public PathMatcher removeRight(int count) {
		Set<PathPattern> patterns = new HashSet<>();
		for (PathPattern pat : this.patterns) {
			pat.doRemoveRight(count, patterns);
		}
		return new PathMatcher(Collections.unmodifiableSet(patterns));
	}
}
