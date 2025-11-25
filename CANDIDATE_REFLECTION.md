# Backend Engineer Challenge - Candidate Reflection

## 1. Trade-offs

The most significant trade-off was in Challenge 4 (Deadlock Prevention), where I chose between a simple one-line fix (reordering locks in method2) versus implementing a comprehensive ordered locking system. The simple fix would have zero overhead and solve the immediate problem, but I opted for the ordered locking system with `LockOrder`, `OrderedLock`, and `LockAcquisitionHelper` classes. This added ~200 lines of code and introduces small overhead (~2ms), but it prevents the entire pattern that causes deadlocks rather than just fixing this instance. In a production system, this approach is more maintainable and prevents future developers from accidentally reintroducing the same bug.

In Challenge 5 (Connection Pool), I faced a cost-benefit decision where pool size 100 had the best latency (1009ms P95) but pool size 50 achieved 1504ms—only 33% slower—while using 50% fewer resources. I recommended size 50 because the marginal performance gain didn't justify doubling resource consumption, especially since my testing revealed query execution time (500ms), not pool size, was the real bottleneck.

## 2. Performance & Resilience

My diagnostic approach starts with immediate stabilization followed by root cause analysis. First, I'd check the `ConnectionPoolMonitor` I built for pool exhaustion or high utilization, review logs for exceptions and timeouts, and if needed, temporarily increase pool size as an emergency measure. For root cause analysis, I'd use the monitoring solution to identify whether threads are waiting for connections, capture slow query logs, and take heap dumps to identify memory issues. In my testing, I discovered that query execution time was the actual bottleneck—threads were waiting for queries to complete, not for connections.

I would prioritize: (1) stopping immediate user impact like pool exhaustion that blocks all operations, (2) reducing blast radius with circuit breakers and timeouts, (3) fixing the root cause through query optimization or resource scaling, and (4) adding monitoring for early detection. The key lesson from Challenge 5 was that performance problems often have layers—just increasing pool size masks deeper issues like the need for query optimization or caching.

## 3. Impact & Value

Each technical solution directly addresses business risks and user experience. The session management fixes (Challenge 1) prevent race conditions that could create duplicate sessions, security vulnerabilities, and user confusion—eliminating support tickets and increasing trust. The memory leak fix (Challenge 2) prevents catastrophic OutOfMemoryErrors that would cause service downtime and lost revenue, while enabling reliable horizontal scaling. The priority queue system (Challenge 3) ensures critical errors like payment failures get immediate attention, directly impacting incident response time and user experience during outages.

The deadlock prevention (Challenge 4) eliminates system hangs that require manual service restarts, reducing on-call burden and improving reliability. The connection pool optimization (Challenge 5) provides early warning before users experience timeouts, while reducing infrastructure costs by 50%. More importantly, identifying query execution as the real bottleneck points to where optimization effort would have the most impact. Together, these changes improve reliability, reduce operational costs, and enhance user experience—enabling the business to scale without proportional increases in operational complexity.

## 4. AI Usage

I used Claude extensively throughout this challenge as a learning tool. Coming into this with basic multi-threading knowledge but limited production experience, I used Claude to understand fundamentals (like Coffman's deadlock conditions and why lock ordering works), help structure solutions, debug issues, and learn tools like heap dump analysis. However, I never simply copied code—I would take suggested approaches, implement them myself, then validate my understanding by explaining the code back to Claude and asking it to identify conceptual gaps.

My validation process included: writing detailed work logs explaining how each solution works, intentionally breaking aspects to verify I understood what would happen, writing comprehensive tests (like the 50-run deadlock frequency test), and ensuring I could explain design decisions without referring to notes. While Claude significantly accelerated my learning, I can confidently say I understand every line of code submitted and could explain the reasoning in a technical interview, modify it for different requirements, or debug issues. I treated this challenge as an opportunity to build genuine expertise in concurrent programming and memory management—skills I'll carry forward in my career.

## 5. Measurability & Customer Focus

I would track both technical health metrics and user experience indicators. Technical metrics include: connection pool utilization (<70% target), threads waiting for connections (0 target), heap utilization (<75%), cache hit rate (>80%), lock acquisition timeouts (0 target), and P95 response times (<1500ms). I'd set up alerts for pool utilization >80%, heap >85%, critical logs waiting >10s, or any timeout failures. These would be exported to Prometheus with Grafana dashboards and Elastalert for critical alerting.

For user experience, I'd track uptime (99.9%+ target), error rates by endpoint (<0.1%), unexpected logout rates, and query latencies. The ultimate validation would be decreased support tickets for session issues and timeouts, improved user retention from better uptime, and reduced infrastructure costs (the 50% connection pool reduction directly lowers expenses). The key insight is that technical metrics predict user experience—connection pool exhaustion causes timeouts, memory leaks cause crashes, deadlocks cause hangs. By monitoring these technical signals, we can detect and resolve issues before users are significantly impacted.
