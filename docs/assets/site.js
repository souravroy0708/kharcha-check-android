/* Kharcha Check website script.
   Runs entirely in the browser: no network requests, no cookies, no storage.
   The message reader is a small JavaScript version of the app's own parser (SmsParser.kt). */
(function () {
  "use strict";
  document.documentElement.classList.add("js");

  /* Footer year */
  var y = document.querySelector("[data-year]");
  if (y) y.textContent = String(new Date().getFullYear());

  /* Close the mobile menu after picking a link */
  var menu = document.querySelector(".menu");
  if (menu) {
    menu.addEventListener("click", function (e) {
      if (e.target.closest("a")) menu.removeAttribute("open");
    });
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape") menu.removeAttribute("open");
    });
  }

  /* ---------- Message reader ---------- */
  var SAMPLES = [
    "Rs.389.00 debited from A/c XX4821 on 14-11-26 to VPA swiggy@icici (Swiggy). UPI Ref No 630518920112. Not you? Call 18002586161",
    "Paid ₹40 to Ramesh Tea Stall via UPI from HDFC Bank A/c **7731. UPI Ref 4319 0277 5512",
    "INR 2,499.00 spent on your SBI Credit Card ending 5523 at AMAZON PAY INDIA on 12/11/26. Avl limit: INR 41,230.50",
    "Your a/c no. XXXXXXXX9012 is debited for Rs.1,180.00 on 10-11-2026 towards TGSPDCL electricity bill. Ref no 51846629301"
  ];

  var CATS = {
    food_delivery: ["Food delivery", "🛵", "leak"], chai_coffee: ["Chai & coffee outside", "☕", "leak"],
    groceries: ["Groceries & ration", "🛒", "need"], cab: ["Cabs", "🚕", "want"], commute: ["Commute", "🚇", "need"],
    fuel: ["Fuel", "⛽", "need"], shopping: ["Shopping", "🛍️", "want"], shopping_clothes: ["Clothes & footwear", "👕", "want"],
    personal_care: ["Salon & grooming", "💇", "want"], mobile_internet: ["Mobile & internet", "📶", "need"],
    electricity: ["Electricity & water", "💡", "need"], gas: ["Gas / LPG", "🔥", "need"], rent: ["Rent & housing", "🏠", "need"],
    emi: ["EMI & loan", "🏦", "need"], ott: ["OTT & music subscriptions", "📺", "want"], medical: ["Medicine & doctor", "💊", "need"],
    entertainment: ["Movies & events", "🎬", "want"], betting: ["Betting & fantasy apps", "🎲", "leak"],
    insurance: ["Insurance premium", "🛡️", "need"], sip: ["SIP / mutual funds", "📈", "invest"], stocks: ["Stocks", "📊", "invest"],
    income: ["Income", "💰", "income"], uncategorised: ["Uncategorised (you pick)", "❓", "want"]
  };
  var BUCKET = { need: "Need", want: "Want", leak: "Leak", invest: "Invest", income: "Income" };

  // Order matters: specific brands before umbrella brands (same as the app).
  var BRANDS = [
    ["Swiggy Instamart", "groceries", ["instamart"]], ["Swiggy", "food_delivery", ["bundl technologies", "swiggy"]],
    ["Zomato", "food_delivery", ["zomato", "eternal ltd"]], ["Blinkit", "groceries", ["blinkit", "grofers"]],
    ["Zepto", "groceries", ["zepto", "kiranakart"]], ["BigBasket", "groceries", ["bigbasket"]], ["DMart", "groceries", ["dmart", "d-mart", "avenue supermarts"]],
    ["Starbucks", "chai_coffee", ["starbucks"]], ["Chai", "chai_coffee", ["chai", "tea stall", "chaayos", "coffee"]],
    ["Uber", "cab", ["uber"]], ["Ola", "cab", ["olacabs", "ani technologies"]], ["Rapido", "cab", ["rapido"]],
    ["Metro / Rail", "commute", ["metro rail", "hmrl", "bmrcl", "dmrc", "irctc"]],
    ["Fuel", "fuel", ["indian oil", "iocl", "hpcl", "bpcl", "petrol", "fuel"]],
    ["Amazon", "shopping", ["amazon"]], ["Flipkart", "shopping", ["flipkart"]], ["Myntra", "shopping_clothes", ["myntra"]],
    ["Ajio", "shopping_clothes", ["ajio", "zudio"]], ["Nykaa", "personal_care", ["nykaa"]],
    ["Airtel", "mobile_internet", ["airtel"]], ["Jio", "mobile_internet", ["reliance jio", "jio prepaid"]],
    ["Electricity", "electricity", ["bescom", "tsspdcl", "tgspdcl", "tata power", "adani electricity", "msedcl", "electricity"]],
    ["Gas", "gas", ["indane", "bharatgas", "hp gas", "mahanagar gas"]], ["Rent", "rent", ["house rent", "nobroker", "rent"]],
    ["Loan EMI", "emi", ["bajaj finance", "loan emi", "emi"]], ["Netflix", "ott", ["netflix"]], ["Spotify", "ott", ["spotify"]],
    ["YouTube Premium", "ott", ["youtube"]], ["JioHotstar", "ott", ["hotstar"]],
    ["Pharmacy", "medical", ["apollo pharmacy", "1mg", "pharmeasy", "medplus", "netmeds"]],
    ["BookMyShow", "entertainment", ["bookmyshow", "pvr"]], ["Dream11", "betting", ["dream11", "my11circle", "mpl"]],
    ["Insurance", "insurance", ["lic of india", "insurance"]],
    ["Mutual Fund SIP", "sip", ["mutual fund", "bse star mf", "groww", "kuvera", "coin by zerodha"]],
    ["Stocks", "stocks", ["zerodha", "upstox", "angel one"]]
  ];

  var EXCLUDE = /\b(otp|one time password|verification code|cvv|do not share|min(?:imum)? amount due|total amount due|bill (?:is )?generated|payment due|failed|declined|unsuccessful|will be debited|pre-approved|loan offer|offer valid)\b/i;
  var AMOUNT = /(?:rs\.?|inr|₹)\s*([0-9]{1,3}(?:,[0-9]{2,3})+(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)/i;
  var PAYEE = [
    /\(([A-Za-z][A-Za-z0-9 .&'\-]{2,40})\)/,
    /(?:\bto|\bat|towards|paid to|info:?)\s+(?:vpa\s+)?([A-Za-z0-9@._\-&' ]{2,40}?)(?=\s+(?:on|ref|upi|utr|via|avl|for|using|from|electricity|bill)\b|[.;,(]|$)/i,
    /\bfrom\s+(?:vpa\s+)?([A-Za-z0-9@._\-&' ]{2,40}?)(?=\s+(?:on|ref|upi|utr|via|avl)\b|[.;,(]|$)/i
  ];

  function rupees(n, decimals) {
    return "₹" + n.toLocaleString("en-IN", { minimumFractionDigits: decimals || 0, maximumFractionDigits: decimals || 0 });
  }
  function short(n) {
    if (n >= 1e7) return "₹" + (n / 1e7).toFixed(2).replace(/\.?0+$/, "") + " crore";
    if (n >= 1e5) return "₹" + (n / 1e5).toFixed(1).replace(/\.0$/, "") + " lakh";
    return rupees(Math.round(n));
  }
  function titleCase(s) {
    return s.toLowerCase().replace(/\b[a-z]/g, function (c) { return c.toUpperCase(); });
  }
  // Mask long digit runs (account, card, reference and phone numbers), but never the amount.
  function mask(text, amountRaw) {
    var keep = amountRaw ? text.indexOf(amountRaw) : -1;
    var out = text.replace(/\d{4,}(?: \d{3,})*/g, function (m, offset) {
      if (keep >= 0 && offset >= keep && offset < keep + amountRaw.length) return m;
      if (/[\/\-.,]/.test(text.charAt(offset - 1))) return m; // part of a date or an amount
      return "XXXX";
    });
    return out.replace(/[xX*]{2,}\s?XXXX/g, "XXXX");
  }

  function parse(raw) {
    var text = raw.replace(/\s+/g, " ").trim();
    if (text.length < 12) return { error: "That’s too short to be a bank message. Paste the whole message." };
    if (EXCLUDE.test(text)) return { error: "This looks like an OTP, an offer, a bill reminder or a failed payment, so no entry is made. The app skips these too." };
    var lower = text.toLowerCase();
    var debit = /\b(debited|spent|paid|sent|withdrawn|purchase|dr\b|debit of|transferred to)/.test(lower);
    var credit = /\b(credited|received|deposited|cr\b|added to)/.test(lower);
    var refund = /\b(refund(?:ed)?|reversed|reversal)\b/.test(lower);
    if (!debit && !credit && !refund) return { error: "No money movement found. Look for words like debited, paid or credited in the message." };
    var am = text.match(AMOUNT);
    if (!am) return { error: "Couldn’t find an amount. In the app you’d add this one manually." };
    var amount = parseFloat(am[1].replace(/,/g, ""));
    if (!(amount > 0)) return { error: "The amount is zero, so there’s nothing to add." };

    var incoming = refund || (credit && !debit);
    var payee = "";
    for (var i = 0; i < PAYEE.length && !payee; i++) {
      var m = text.match(PAYEE[i]);
      if (m) payee = m[1].trim();
    }
    var fromVpa = /@/.test(payee);
    if (fromVpa) payee = payee.split("@")[0];
    if (/^(your|a\/c|ac|account|card)\b/i.test(payee)) payee = "";

    var key = "uncategorised", brand = "";
    var hay = (payee + " " + lower).toLowerCase();
    for (var b = 0; b < BRANDS.length; b++) {
      var words = BRANDS[b][2];
      for (var w = 0; w < words.length; w++) {
        if (hay.indexOf(words[w]) >= 0) { key = BRANDS[b][1]; brand = BRANDS[b][0]; break; }
      }
      if (brand) break;
    }
    if (incoming) key = "income";
    if (!payee) payee = brand || "Not found, you’d type it";
    else if (fromVpa && brand && brand !== "Chai") payee = brand;
    else if (/\s/.test(payee) && (payee === payee.toUpperCase() || payee === payee.toLowerCase())) payee = titleCase(payee);

    var mode = /credit card/.test(lower) ? "Credit card"
      : /debit card/.test(lower) ? "Debit card"
      : /\b(upi|vpa)\b/.test(lower) ? "UPI"
      : /\b(atm|cash)\b/.test(lower) ? "Cash"
      : /\b(neft|imps|rtgs|nach|ach)\b/.test(lower) ? "Bank transfer" : "Bank account";

    return {
      amount: amount, incoming: incoming, refund: refund, payee: payee, key: key, mode: mode,
      masked: mask(text, am[0])
    };
  }

  var msg = document.getElementById("msg");
  var readBtn = document.querySelector("[data-read]");
  var result = document.querySelector("[data-result]");
  if (!msg || !readBtn || !result) return;
  var note = result.querySelector("[data-msg]");
  var draft = result.querySelector("[data-draft]");
  var masked = result.querySelector("[data-masked]");
  var habit = result.querySelector("[data-habit]");
  var habitOut = result.querySelector("[data-habit-out]");
  var per = 52;
  var last = null;

  function field(name, value) {
    var el = draft.querySelector('[data-f="' + name + '"]');
    el.textContent = "";
    if (typeof value === "string") el.textContent = value;
    else el.appendChild(value);
  }

  function renderHabit() {
    if (!last || last.incoming) { habit.hidden = true; return; }
    habit.hidden = false;
    var yearly = last.amount * per;
    var monthly = yearly / 12, r = 0.12 / 12, n = 120;
    var fv = monthly * ((Math.pow(1 + r, n) - 1) / r) * (1 + r);
    habitOut.textContent = "";
    var a = document.createElement("span");
    a.textContent = "That’s " + rupees(Math.round(yearly)) + " a year. Invested every month instead, at 12% a year it could grow to";
    var big = document.createElement("b");
    big.className = "habit-big num";
    big.textContent = short(fv);
    var c = document.createElement("span");
    c.textContent = "in 10 years. An example, not a promise: market returns go up and down.";
    habitOut.appendChild(a); habitOut.appendChild(big); habitOut.appendChild(c);
  }

  function read() {
    var out = parse(msg.value);
    result.hidden = false;
    if (out.error) {
      last = null;
      note.textContent = out.error; note.hidden = false;
      draft.hidden = true; masked.hidden = true; habit.hidden = true;
      return;
    }
    last = out;
    note.hidden = true; draft.hidden = false; masked.hidden = false;
    field("amount", rupees(out.amount, out.amount % 1 ? 2 : 0));
    field("type", out.refund ? "Refund (money back)" : out.incoming ? "Money in" : "Expense");
    field("payee", out.payee);
    var cat = CATS[out.key];
    var wrap = document.createElement("span");
    wrap.textContent = cat[1] + " " + cat[0];
    var tag = document.createElement("span");
    tag.className = "bucket bucket-" + cat[2];
    tag.textContent = BUCKET[cat[2]];
    wrap.appendChild(tag);
    field("category", wrap);
    field("mode", out.mode);
    masked.textContent = "Account, card and reference numbers are masked before anything is saved: ";
    var code = document.createElement("code");
    code.textContent = out.masked;
    masked.appendChild(code);
    renderHabit();
  }

  readBtn.addEventListener("click", read);
  msg.addEventListener("keydown", function (e) {
    if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) { e.preventDefault(); read(); }
  });

  function pressGroup(container, btn) {
    container.querySelectorAll("button").forEach(function (b) { b.setAttribute("aria-pressed", b === btn ? "true" : "false"); });
  }
  var samples = document.querySelector("[data-samples]");
  if (samples) samples.addEventListener("click", function (e) {
    var btn = e.target.closest("button[data-sample]");
    if (!btn) return;
    pressGroup(samples, btn);
    msg.value = SAMPLES[Number(btn.getAttribute("data-sample"))];
    read();
  });
  msg.addEventListener("input", function () {
    if (samples) samples.querySelectorAll("button").forEach(function (b) { b.setAttribute("aria-pressed", "false"); });
  });
  var freq = document.querySelector("[data-freq]");
  if (freq) freq.addEventListener("click", function (e) {
    var btn = e.target.closest("button[data-per]");
    if (!btn) return;
    pressGroup(freq, btn);
    per = Number(btn.getAttribute("data-per"));
    renderHabit();
  });

  read(); // show the first sample already read, so the result is visible straight away
})();
