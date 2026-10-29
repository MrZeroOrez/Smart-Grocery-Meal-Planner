import React, { useState, useEffect, useRef, FormEvent } from "react";
import { createRoot } from "react-dom/client";
import "./style.css";
type Row = Record<string, any>;
type Session = { accessToken: string; userId: string };
type RequestFn = (
  path: string,
  method?: string,
  body?: unknown,
  key?: string,
) => Promise<any>;
const euro = (n: number | string) =>
  new Intl.NumberFormat("en-NL", { style: "currency", currency: "EUR" }).format(
    Number(n || 0),
  );
const form = (e: FormEvent<HTMLFormElement>) => {
  e.preventDefault();
  return Object.fromEntries(new FormData(e.currentTarget));
};
function normal(v: any): any {
  if (Array.isArray(v)) return v.map(normal);
  if (v && typeof v === "object")
    return Object.fromEntries(
      Object.entries(v).map(([k, x]) => [
        k === k.toUpperCase() ? k.toLowerCase() : k,
        normal(x),
      ]),
    );
  return v;
}
function App() {
  const [session, setSession] = useState<Session | null>(null),
    [register, setRegister] = useState(false),
    [role, setRole] = useState("CUSTOMER"),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState("");
  const request: RequestFn = async (path, method = "GET", body, key) => {
    const res = await fetch("/api/v1" + path, {
      method,
      headers: {
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
        ...(session ? { Authorization: "Bearer " + session.accessToken } : {}),
        ...(key ? { "Idempotency-Key": key } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!res.ok) {
      let message = "Request failed (" + res.status + ")";
      try {
        const b = await res.json();
        message = b.error || message;
      } catch {}
      if (res.status === 401 && session) setSession(null); setRegister(false);
      throw Error(message);
    }
    return res.status === 204 || res.headers.get("content-length") === "0"
      ? null
      : await res.text().then((t) => (t ? normal(JSON.parse(t)) : null));
  };
  async function run(fn: () => Promise<void>) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await fn();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Request failed");
    } finally {
      setBusy(false);
    }
  }
  async function login(e: FormEvent<HTMLFormElement>) {
    const b = form(e);
    await run(async () => {
      const res = await request(
        "/auth/" + (register ? "register" : "login"),
        "POST",
        b,
      );
      const me = await fetch("/api/v1/auth/me", {
        headers: { Authorization: "Bearer " + res.accessToken },
      }).then((r) => r.json());
      setRole(me.role);
      setSession(res);
    });
  }
  return (
    <>
      <header>
        <div>
          <span className="mark">◈</span>
          <b>FreshWeek</b>
          <small>JAVA + REACT / TYPESCRIPT</small>
        </div>
        <div className="inline">
          <span className="badge">LOCAL SIMULATION</span>
          {session && (
            <button
              disabled={busy}
              onClick={() =>
                run(async () => {
                  await request("/auth/logout", "POST");
                  setSession(null); setRegister(false);
                })
              }
            >
              Log out
            </button>
          )}
        </div>
      </header>
      <main>
        {!session ? (
          <div className="welcome">
            <section>
              <p className="eyebrow">SHOP SMARTER, PLAN BETTER</p>
              <h1>Good food starts with a little planning.</h1>
              <p className="muted">
                Build a weekly menu, turn ingredients into a shopping cart, and
                explore reliable grocery checkout.
              </p>
              <div className="benefits">
                <span>✓ Weekly meals and dietary preferences</span>
                <span>✓ Compatible product substitutions</span>
                <span>✓ Stock reservations and simulated checkout</span>
              </div>
              <p className="hint">
                Persistent SQL data. No real payments. Register a customer
                account or sign in with the administrator configured on the
                server.
              </p>
            </section>
            <form className="card stack" onSubmit={login}>
              <h2>{register ? "Create your account" : "Welcome back"}</h2>
              <label>
                Email
                <input
                  type="email"
                  name="email"
                  autoComplete="username"
                  required
                />
              </label>
              <label>
                Password
                <input
                  type="password"
                  name="password"
                  minLength={12}
                  maxLength={72}
                  autoComplete={register ? "new-password" : "current-password"}
                  required
                />
              </label>
              <small>12-72 characters, maximum 72 UTF-8 bytes.</small>
              <button disabled={busy} className="primary">
                {register ? "Register" : "Sign in"}
              </button>
              <button type="button" onClick={() => setRegister(!register)}>
                {register
                  ? "Already registered? Sign in"
                  : "New here? Create an account"}
              </button>
            </form>
          </div>
        ) : (
          <Workspace
            request={request}
            run={run}
            busy={busy}
            notice={setNotice}
            role={role}
          />
        )}
        {error && (
          <div role="alert" className="alert error">
            {error}
          </div>
        )}
        {notice && (
          <div role="status" className="alert success">
            {notice}
          </div>
        )}
      </main>
      <footer>
        Learning application · Simulated transactions · Session expires after 15
        minutes; refresh requires sign-in
      </footer>
    </>
  );
}
type Props = {
  request: RequestFn;
  run: (fn: () => Promise<void>) => Promise<void>;
  busy: boolean;
  notice: (s: string) => void;
  role: string;
};
function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="card stat">
      <small>{label}</small>
      <strong>{value}</strong>
    </div>
  );
}
function Table({
  heads,
  rows,
}: {
  heads: string[];
  rows: React.ReactNode[][];
}) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {heads.map((h) => (
              <th key={h}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i}>
              {r.map((c, j) => (
                <td key={j}>{c}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {!rows.length && <p className="empty">No records yet.</p>}
    </div>
  );
}
function Workspace({ request, run, busy, notice, role }: Props) {
  const [tab, setTab] = useState("Shop"),
    [products, setProducts] = useState<Row[]>([]),
    [cart, setCart] = useState<Row[]>([]),
    [recipes, setRecipes] = useState<Row[]>([]),
    [plans, setPlans] = useState<Row[]>([]),
    [orders, setOrders] = useState<Row[]>([]),
    [diet, setDiet] = useState("ANY"),
    [q, setQ] = useState(""),
    [category, setCategory] = useState(""),
    [week, setWeek] = useState(new Date().toISOString().slice(0, 10)),
    [alternatives, setAlternatives] = useState<Row[]>([]),
    [replace, setReplace] = useState("");
  const pending = useRef<{ fingerprint: string; key: string } | null>(null);
  async function refresh() {
    const [p, c, r, ps, o, pref] = await Promise.all([
      request("/products"),
      request("/cart"),
      request("/recipes"),
      request("/plan?week=" + week),
      request("/orders"),
      request("/preferences"),
    ]);
    setProducts(p);
    setCart(c);
    setRecipes(r);
    setPlans(ps);
    setOrders(o);
    setDiet(pref.diet);
  }
  useEffect(() => {
    run(refresh);
  }, [week]);
  const visible = products.filter(
    (p) =>
      p.name.toLowerCase().includes(q.toLowerCase()) &&
      (!category || p.category === category) &&
      (diet === "ANY" ||
        p.diet === diet ||
        (diet === "VEGETARIAN" && p.diet === "VEGAN")),
  );
  async function change(id: string, quantity: number) {
    await run(async () => {
      await request("/cart/" + id, "PUT", { quantity });
      await refresh();
    });
  }
  async function reserve() {
    const fingerprint = JSON.stringify(cart);
    if (pending.current?.fingerprint !== fingerprint)
      pending.current = { fingerprint, key: crypto.randomUUID() };
    await run(async () => {
      const o = await request(
        "/reservations",
        "POST",
        undefined,
        pending.current!.key,
      );
      pending.current = null;
      await refresh();
      setTab("Orders");
      notice(
        "Stock reserved for 10 minutes. Confirm simulated payment or cancel. Order " +
          o.id,
      );
    });
  }
  async function meal(e: FormEvent<HTMLFormElement>) {
    const b = form(e);
    await run(async () => {
      await request("/plan", "PUT", { ...b, servings: Number(b.servings) });
      await refresh();
      notice("Meal saved");
    });
  }
  async function toCart() {
    const fingerprint = week + JSON.stringify(plans);
    if (pending.current?.fingerprint !== fingerprint)
      pending.current = { fingerprint, key: crypto.randomUUID() };
    await run(async () => {
      await request("/plan/cart", "POST", { week }, pending.current!.key);
      pending.current = null;
      await refresh();
      setTab("Cart");
      notice("Ingredients combined and added atomically to cart");
    });
  }
  return (
    <>
      <div className="toolbar">
        <div>
          <p className="eyebrow">YOUR WEEK, MADE EASIER</p>
          <h1>Fresh ideas. One simple shop.</h1>
        </div>
        <label>
          Dietary preference
          <select
            aria-label="Dietary preference"
            value={diet}
            disabled={busy}
            onChange={(e) =>
              run(async () => {
                await request("/preferences", "PUT", { diet: e.target.value });
                await refresh();
              })
            }
          >
            <option value="ANY">Any diet</option>
            <option value="VEGETARIAN">Vegetarian</option>
            <option value="VEGAN">Vegan</option>
          </select>
        </label>
      </div>
      <nav>
        {[
          "Shop",
          "Cart",
          "Meal planner",
          "Orders",
          ...(role === "ADMIN" ? ["Inventory"] : []),
        ].map((t) => (
          <button
            disabled={busy}
            className={tab === t ? "active" : ""}
            key={t}
            onClick={() => setTab(t)}
          >
            {t}
            {t === "Cart" ? " (" + cart.length + ")" : ""}
          </button>
        ))}
        <button disabled={busy} onClick={() => run(refresh)}>
          Refresh
        </button>
      </nav>
      <div className="stats">
        <Stat
          label="CART ITEMS"
          value={cart.reduce((n, i) => n + i.quantity, 0)}
        />
        <Stat
          label="CART VALUE"
          value={euro(cart.reduce((n, i) => n + Number(i.subtotal), 0))}
        />
        <Stat label="PLANNED MEALS" value={plans.length} />
        <Stat label="ORDERS" value={orders.length} />
      </div>
      {tab === "Shop" && (
        <>
          <div className="filters">
            <label>
              Search products
              <input
                value={q}
                placeholder="Rice, chickpeas, milk…"
                onChange={(e) => setQ(e.target.value)}
              />
            </label>
            <label>
              Category
              <select
                aria-label="Category"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
              >
                <option value="">All categories</option>
                {[...new Set(products.map((p) => p.category))].map((c) => (
                  <option key={c}>{c}</option>
                ))}
              </select>
            </label>
          </div>
          <div className="products">
            {visible.map((p) => (
              <article className="card product" key={p.id}>
                <div className="product-art">
                  {p.category === "PRODUCE"
                    ? "◉"
                    : p.category === "DAIRY"
                      ? "▥"
                      : "◈"}
                </div>
                <div className="row">
                  <small>{p.category}</small>
                  <span className="pill">{p.diet}</span>
                </div>
                <h3>{p.name}</h3>
                <p>
                  <strong>{euro(p.price)}</strong> / {p.unit}
                </p>
                <small>{p.stock} available</small>
                <button
                  className="primary"
                  disabled={busy || p.stock === 0}
                  onClick={() =>
                    change(
                      p.id,
                      (cart.find((i) => i.productId === p.id)?.quantity || 0) +
                        1,
                    )
                  }
                >
                  Add to cart
                </button>
                {p.stock === 0 && (
                  <button
                    disabled={busy}
                    onClick={() =>
                      run(async () => {
                        setAlternatives(
                          await request("/products/" + p.id + "/substitutes"),
                        );
                        setReplace(p.id);
                        setTab("Cart");
                      })
                    }
                  >
                    Find alternatives
                  </button>
                )}
              </article>
            ))}
          </div>
        </>
      )}
      {tab === "Cart" && (
        <section className="card">
          <h2>Your shopping cart</h2>
          <Table
            heads={["Product", "Price", "Quantity", "Subtotal", "Actions"]}
            rows={cart.map((i) => [
              i.name,
              euro(i.price),
              <input
                aria-label={"Quantity for " + i.name}
                type="number"
                min="0"
                max="100"
                value={i.quantity}
                disabled={busy}
                onChange={(e) => change(i.productId, Number(e.target.value))}
              />,
              euro(i.subtotal),
              <div className="inline">
                <button disabled={busy} onClick={() => change(i.productId, 0)}>
                  Remove
                </button>
                <button
                  disabled={busy}
                  onClick={() =>
                    run(async () => {
                      setReplace(i.productId);
                      setAlternatives(
                        await request(
                          "/products/" + i.productId + "/substitutes",
                        ),
                      );
                    })
                  }
                >
                  Substitute
                </button>
              </div>,
            ])}
          />
          <div className="row section-gap">
            <strong>
              Total {euro(cart.reduce((n, i) => n + Number(i.subtotal), 0))}
            </strong>
            <button
              className="primary"
              disabled={busy || !cart.length}
              onClick={reserve}
            >
              Reserve &amp; checkout
            </button>
          </div>
          <p className="hint">
            Prices are calculated by the server when reserving. No card or
            payment provider is connected.
          </p>
          {replace && (
            <div className="subtle">
              <h3>Compatible alternatives</h3>
              {alternatives.length ? (
                alternatives.map((p) => (
                  <div className="row" key={p.id}>
                    <span>
                      {p.name} · {euro(p.price)} / {p.unit}
                    </span>
                    <button
                      disabled={busy}
                      onClick={() =>
                        run(async () => {
                          if (cart.some((i) => i.productId === replace))
                            await request(
                              "/cart/" + replace + "/substitute/" + p.id,
                              "POST",
                            );
                          else
                            await request("/cart/" + p.id, "PUT", {
                              quantity: 1,
                            });
                          await refresh();
                          setReplace("");
                          notice("Substitution added");
                        })
                      }
                    >
                      Use alternative
                    </button>
                  </div>
                ))
              ) : (
                <p>
                  No in-stock substitute matching this ingredient, unit and
                  diet.
                </p>
              )}
            </div>
          )}
        </section>
      )}
      {tab === "Meal planner" && (
        <>
          <div className="grid">
            <section className="card">
              <h2>Plan seven days</h2>
              <label>
                Week starting
                <input
                  type="date"
                  value={week}
                  onChange={(e) => setWeek(e.target.value)}
                />
              </label>
              <form className="stack section-gap" onSubmit={meal} key={week}>
                <label>
                  Meal date
                  <input
                    name="date"
                    type="date"
                    min={week}
                    max={new Date(new Date(week).getTime() + 6 * 86400000)
                      .toISOString()
                      .slice(0, 10)}
                    defaultValue={week}
                    required
                  />
                </label>
                <label>
                  Slot
                  <select aria-label="Meal slot" name="slot">
                    <option>BREAKFAST</option>
                    <option>LUNCH</option>
                    <option>DINNER</option>
                  </select>
                </label>
                <label>
                  Recipe
                  <select aria-label="Recipe" name="recipeId">
                    {recipes.map((r) => (
                      <option key={r.id} value={r.id}>
                        {r.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Servings
                  <input
                    name="servings"
                    type="number"
                    min="1"
                    max="10"
                    defaultValue="1"
                    required
                  />
                </label>
                <button className="primary" disabled={busy}>
                  Save meal
                </button>
              </form>
            </section>
            <section className="card">
              <h2>Your weekly menu</h2>
              <Table
                heads={["Date", "Meal", "Recipe", "Servings", ""]}
                rows={plans.map((p) => [
                  p.date,
                  p.slot,
                  p.name,
                  p.servings,
                  <button
                    disabled={busy}
                    onClick={() =>
                      run(async () => {
                        await request(
                          "/plan?date=" + p.date + "&slot=" + p.slot,
                          "DELETE",
                        );
                        await refresh();
                      })
                    }
                  >
                    Remove
                  </button>,
                ])}
              />
              <button
                className="primary section-gap"
                disabled={busy || !plans.length}
                onClick={toCart}
              >
                Add weekly ingredients to cart
              </button>
              <p className="hint">
                Ingredients are aggregated across recipes. One unit means one
                catalogue pack/can/carton per serving in this simplified demo.
                If any ingredient is unavailable, the whole import is rejected.
              </p>
            </section>
          </div>
          <div className="products">
            {recipes.map((r) => (
              <section className="card" key={r.id}>
                <span className="pill">{r.diet}</span>
                <h3>{r.name}</h3>
                <p>{r.instructions}</p>
                <small>
                  {r.ingredients.map((i: Row) => i.ingredient).join(", ")}
                </small>
              </section>
            ))}
          </div>
        </>
      )}
      {tab === "Orders" && (
        <section className="card">
          <h2>Order history</h2>
          <Table
            heads={["Order", "Status", "Total", "Items", "Actions"]}
            rows={orders.map((o) => [
              o.id.slice(0, 8),
              <span className="pill">{o.status}</span>,
              euro(o.total),
              o.lines.map((l: Row) => l.name + " × " + l.quantity).join(", "),
              o.status === "RESERVED" ? (
                <div className="inline">
                  <button
                    className="primary"
                    disabled={busy}
                    onClick={() =>
                      run(async () => {
                        const result = await request(
                          "/orders/" + o.id + "/confirm",
                          "POST",
                        );
                        await refresh();
                        notice("Order " + result.status.toLowerCase());
                      })
                    }
                  >
                    Confirm simulated payment
                  </button>
                  <button
                    disabled={busy}
                    onClick={() =>
                      run(async () => {
                        await request("/orders/" + o.id + "/cancel", "POST");
                        await refresh();
                        notice("Reservation cancelled; stock released");
                      })
                    }
                  >
                    Cancel
                  </button>
                </div>
              ) : (
                "—"
              ),
            ])}
          />
        </section>
      )}
      {tab === "Inventory" && (
        <section className="card">
          <h2>Inventory administration</h2>
          <p className="hint">
            Available stock excludes active reservations. Updating it sets the
            available quantity only.
          </p>
          {products.map((p) => (
            <form
              className="inventory-row"
              key={p.id}
              onSubmit={(e) => {
                const b = form(e);
                run(async () => {
                  await request("/admin/products/" + p.id + "/stock", "PUT", {
                    stock: Number(b.stock),
                  });
                  await refresh();
                  notice("Stock updated");
                });
              }}
            >
              <span>{p.name}</span>
              <label>
                Available stock
                <input
                  name="stock"
                  type="number"
                  min="0"
                  max="100000"
                  defaultValue={p.stock}
                  key={p.stock}
                  required
                />
              </label>
              <button disabled={busy}>Update stock</button>
            </form>
          ))}
        </section>
      )}
    </>
  );
}

createRoot(document.getElementById("root")!).render(<App />);
