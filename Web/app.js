(function () {
  "use strict";

  var SAVE_KEY = "puppy-clicker-web-save-v1";
  var ENDPOINT_KEY = "puppy-clicker-cloud-endpoint";
  var FORMAT = "puppy-clicker-cloud-save";
  var SCHEMA_VERSION = 1;
  var MAX_OFFLINE_SECONDS = 4 * 60 * 60;
  var roster = [];
  var deferredInstall = null;
  var saveTimer = null;

  var UPGRADES = [
    {id:"better_treats",name:"Better Treats",description:"+1 treat per tap",emoji:"🦴",effect:"CLICK",amount:1,type:"COOKIE",baseTreatCost:35},
    {id:"chew_toy",name:"Chew Toy",description:"+1 treat every second",emoji:"🧸",effect:"AUTO",amount:1,type:"COOKIE",baseTreatCost:100},
    {id:"golden_bowl",name:"Golden Bowl",description:"+5 treats per tap",emoji:"🥣",effect:"CLICK",amount:5,type:"COOKIE",baseTreatCost:750},
    {id:"playmate",name:"Playmate",description:"+5 treats every second",emoji:"🐕",effect:"AUTO",amount:5,type:"COOKIE",baseTreatCost:1500},
    {id:"lucky_collar",name:"Lucky Collar",description:"+3 treats per tap",emoji:"🍀",effect:"CLICK",amount:3,type:"TICKET",baseTreatCost:75,rarity:"COMMON",baseTicketCost:2},
    {id:"training_whistle",name:"Training Whistle",description:"+5 treats every second",emoji:"📯",effect:"AUTO",amount:5,type:"TICKET",baseTreatCost:125,rarity:"UNCOMMON",baseTicketCost:2},
    {id:"puppy_power",name:"Puppy Power",description:"+25 treats per tap",emoji:"⚡",effect:"CLICK",amount:25,type:"TICKET",baseTreatCost:250,rarity:"RARE",baseTicketCost:2},
    {id:"dog_park_crew",name:"Dog Park Crew",description:"+25 treats every second",emoji:"🌳",effect:"AUTO",amount:25,type:"TICKET",baseTreatCost:350,rarity:"RARE",baseTicketCost:3},
    {id:"treat_factory",name:"Treat Factory",description:"+100 treats every second",emoji:"🏭",effect:"AUTO",amount:100,type:"TICKET",baseTreatCost:600,rarity:"EPIC",baseTicketCost:3},
    {id:"legendary_snacks",name:"Legendary Snacks",description:"+100 treats per tap",emoji:"✨",effect:"CLICK",amount:100,type:"TICKET",baseTreatCost:900,rarity:"LEGENDARY",baseTicketCost:2}
  ];

  function defaultGame() {
    var upgrades = {};
    UPGRADES.forEach(function (u) { upgrades[u.id] = 0; });
    return {
      puppyName:"Buddy",
      puppyStyle:"classic",
      unlockedPuppies:["classic","golden","poodle","spotty"],
      favoritePuppies:[],
      accessory:"None",
      treats:0,
      lifetimeTreats:0,
      clickPower:1,
      autoPerSecond:0,
      upgrades:upgrades,
      totalShopPurchases:0,
      ticketInventory:{COMMON:0,UNCOMMON:0,RARE:0,EPIC:0,LEGENDARY:0},
      lastTicketDrop:null,
      ticketDropSerial:0,
      totalTicketsFound:0,
      happiness:100,
      fullness:100,
      energy:100,
      cleanliness:100,
      bond:10,
      careActions:0,
      totalTaps:0,
      combo:0,
      bestCombo:0,
      lastTapMs:0,
      parkActive:false,
      parkReadyAtMs:0,
      lastDailyClaimDay:null,
      dailyStreak:0,
      claimedDailyTasks:[],
      redeemedCodeIds:[],
      pupEyeStrikes:0,
      cooldownUntilMs:0,
      prestigeCount:0,
      skillPoints:0,
      prestigeSkills:{TAP_TRAINING:0,AUTO_TRAINING:0,SMART_SHOPPER:0,TICKET_SENSE:0},
      totalPrestigePointsEarned:0,
      afkLastClaimed:0
    };
  }

  var state = defaultGame();
  var meta = {
    playerId: randomId(),
    cloudRevision: 0,
    dirty: false,
    updatedAt: Date.now(),
    lastLocalSaveAt: Date.now()
  };

  function randomId() {
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
    return "pcw-" + Date.now().toString(36) + "-" + Math.random().toString(36).slice(2);
  }

  function clampNumber(value, min, max) {
    value = Number(value);
    if (!Number.isFinite(value)) return min;
    return Math.min(max, Math.max(min, value));
  }

  function mergeGame(input) {
    var base = defaultGame();
    if (!input || typeof input !== "object") return base;
    Object.keys(base).forEach(function (key) {
      if (Object.prototype.hasOwnProperty.call(input, key)) base[key] = input[key];
    });
    base.treats = Math.max(0, Math.floor(Number(base.treats) || 0));
    base.lifetimeTreats = Math.max(0, Math.floor(Number(base.lifetimeTreats) || 0));
    base.clickPower = Math.max(1, Math.floor(Number(base.clickPower) || 1));
    base.autoPerSecond = Math.max(0, Math.floor(Number(base.autoPerSecond) || 0));
    base.unlockedPuppies = Array.from(new Set(Array.isArray(base.unlockedPuppies) ? base.unlockedPuppies : ["classic","golden","poodle","spotty"]));
    base.favoritePuppies = Array.from(new Set(Array.isArray(base.favoritePuppies) ? base.favoritePuppies : []));
    base.claimedDailyTasks = Array.isArray(base.claimedDailyTasks) ? base.claimedDailyTasks : [];
    base.redeemedCodeIds = Array.isArray(base.redeemedCodeIds) ? base.redeemedCodeIds : [];
    base.upgrades = Object.assign({}, defaultGame().upgrades, base.upgrades || {});
    base.ticketInventory = Object.assign({}, defaultGame().ticketInventory, base.ticketInventory || {});
    base.prestigeSkills = Object.assign({}, defaultGame().prestigeSkills, base.prestigeSkills || {});
    ["happiness","fullness","energy","cleanliness","bond"].forEach(function (k) { base[k] = clampNumber(base[k], 0, 100); });
    return base;
  }

  function envelope() {
    return {
      format:FORMAT,
      schemaVersion:SCHEMA_VERSION,
      revision:meta.cloudRevision,
      playerId:meta.playerId,
      updatedAt:new Date(meta.updatedAt).toISOString(),
      game:state
    };
  }

  function loadLocal() {
    try {
      var raw = localStorage.getItem(SAVE_KEY);
      if (!raw) return;
      var saved = JSON.parse(raw);
      if (saved.format === FORMAT && saved.game) {
        state = mergeGame(saved.game);
        meta.playerId = saved.playerId || meta.playerId;
        meta.cloudRevision = Math.max(0, Number(saved.revision) || 0);
        meta.dirty = Boolean(saved._localDirty);
        meta.updatedAt = Date.parse(saved.updatedAt) || Date.now();
        meta.lastLocalSaveAt = Number(saved._lastLocalSaveAt) || meta.updatedAt;
      } else {
        state = mergeGame(saved);
        meta.dirty = true;
      }
      applyOfflineEarnings();
    } catch (error) {
      console.warn("Unable to load Puppy Clicker web save", error);
      toast("Local save could not be read.");
    }
  }

  function applyOfflineEarnings() {
    if (!state.autoPerSecond) return;
    var elapsed = Math.floor((Date.now() - meta.lastLocalSaveAt) / 1000);
    elapsed = Math.min(MAX_OFFLINE_SECONDS, Math.max(0, elapsed));
    var earned = elapsed * state.autoPerSecond;
    if (earned > 0) {
      state.treats += earned;
      state.lifetimeTreats += earned;
      meta.dirty = true;
      toast("Offline: +" + formatNumber(earned) + " Treats");
    }
  }

  function saveLocal(markDirty) {
    if (markDirty !== false) meta.dirty = true;
    meta.updatedAt = Date.now();
    meta.lastLocalSaveAt = Date.now();
    var out = envelope();
    out._localDirty = meta.dirty;
    out._lastLocalSaveAt = meta.lastLocalSaveAt;
    localStorage.setItem(SAVE_KEY, JSON.stringify(out));
    queueRender();
  }

  function queueSave() {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(function () { saveLocal(true); }, 120);
  }

  function queueRender() {
    requestAnimationFrame(render);
  }

  function formatNumber(value) {
    value = Number(value) || 0;
    if (Math.abs(value) < 1000) return Math.floor(value).toString();
    var units = ["K","M","B","T","Qa","Qi"];
    var unit = -1;
    while (Math.abs(value) >= 1000 && unit < units.length - 1) {
      value /= 1000;
      unit++;
    }
    return value.toFixed(value >= 100 ? 0 : value >= 10 ? 1 : 2).replace(/\.0+$/,"") + units[unit];
  }

  function level() {
    return 1 + Math.floor(Math.sqrt(Math.max(0, state.lifetimeTreats) / 100));
  }

  function careScore() {
    return Math.round((state.happiness + state.fullness + state.energy + state.cleanliness) / 4);
  }

  function mood() {
    var score = careScore();
    if (score >= 90 && state.bond >= 70) return "Best friend";
    if (score >= 85) return "Thrilled";
    if (score >= 65) return "Happy";
    if (score >= 40) return "Okay";
    if (score >= 20) return "Needs care";
    return "Very tired";
  }

  function styleFromAssetId(id) {
    return id.indexOf("v1_") === 0 ? id.slice(3) : id;
  }

  function assetIdFromStyle(style) {
    if (style.indexOf("v2_") === 0) return style;
    return "v1_" + style;
  }

  function puppyForStyle(style) {
    var assetId = assetIdFromStyle(style);
    for (var i = 0; i < roster.length; i++) {
      if (roster[i].assetId === assetId) return roster[i];
    }
    return null;
  }

  function currentPuppyImage() {
    var pup = puppyForStyle(state.puppyStyle);
    return pup ? pup.path : "./assets/logos/puppy_clicker.png";
  }

  async function loadRoster() {
    try {
      var results = await Promise.all([
        fetch("./assets/v1/manifest.json").then(function (r) { return r.json(); }),
        fetch("./assets/v2/manifest.json").then(function (r) { return r.json(); })
      ]);
      roster = results[0].map(function (p) {
        return {number:p.number,name:p.name,assetId:p.asset_id,styleId:styleFromAssetId(p.asset_id),path:"./assets/v1/" + p.filename};
      }).concat(results[1].puppies.map(function (p) {
        return {number:p.number,name:p.name,assetId:p.asset_id,styleId:p.asset_id,path:"./assets/v2/" + p.file};
      }));
    } catch (error) {
      console.warn("Roster manifests unavailable", error);
      roster = [
        {number:1,name:"Buddy / Classic",assetId:"v1_classic",styleId:"classic",path:"./assets/logos/puppy_clicker.png"},
        {number:2,name:"Sunny",assetId:"v1_golden",styleId:"golden",path:"./assets/logos/puppy_clicker.png"},
        {number:3,name:"Mochi",assetId:"v1_poodle",styleId:"poodle",path:"./assets/logos/puppy_clicker.png"},
        {number:4,name:"Pepper",assetId:"v1_spotty",styleId:"spotty",path:"./assets/logos/puppy_clicker.png"}
      ];
    }
    renderRoster();
    render();
  }

  function render() {
    text("treats", formatNumber(state.treats));
    text("shopTreats", formatNumber(state.treats) + " Treats");
    text("clickPower", formatNumber(state.clickPower));
    text("autoPerSecond", formatNumber(state.autoPerSecond));
    text("level", level());
    text("puppyName", state.puppyName || "Buddy");
    text("moodBadge", mood());
    text("careScore", careScore() + "%");
    text("tapGain", "+" + formatNumber(state.clickPower));
    text("revisionBadge", "Revision " + meta.cloudRevision);
    var img = document.getElementById("puppyImage");
    if (img && img.getAttribute("src") !== currentPuppyImage()) img.src = currentPuppyImage();
    var unlocked = state.unlockedPuppies.length;
    text("rosterCount", unlocked + " unlocked");
    text("syncLabel", cloudEndpoint() ? (meta.dirty ? "Sync pending" : "Cloud synced") : "Local save");
    var dot = document.getElementById("syncDot");
    if (dot) dot.style.background = cloudEndpoint() ? (meta.dirty ? "#f7d775" : "#72e6a5") : "#8fa9b7";
    renderShop();
  }

  function renderRoster() {
    var grid = document.getElementById("rosterGrid");
    if (!grid) return;
    grid.innerHTML = "";
    roster.forEach(function (p) {
      var unlocked = state.unlockedPuppies.indexOf(p.styleId) !== -1;
      var card = document.createElement("article");
      card.className = "puppy-card" + (unlocked ? "" : " locked") + (state.puppyStyle === p.styleId ? " active" : "");
      var img = document.createElement("img");
      img.src = p.path;
      img.alt = p.name;
      img.loading = "lazy";
      img.onerror = function () { this.onerror = null; this.src = "./assets/logos/puppy_clicker.png"; };
      var h3 = document.createElement("h3");
      h3.textContent = "#" + p.number + " " + p.name;
      var desc = document.createElement("p");
      desc.textContent = unlocked ? p.assetId : "Locked · " + p.assetId;
      var button = document.createElement("button");
      button.textContent = state.puppyStyle === p.styleId ? "Selected" : unlocked ? "Use puppy" : "Locked";
      button.disabled = !unlocked || state.puppyStyle === p.styleId;
      button.addEventListener("click", function () {
        state.puppyStyle = p.styleId;
        state.puppyName = p.name.split(" / ")[0];
        saveLocal(true);
        renderRoster();
      });
      card.append(img,h3,desc,button);
      grid.appendChild(card);
    });
  }

  function treatCost(upgrade) {
    var owned = Number(state.upgrades[upgrade.id]) || 0;
    var growth = upgrade.type === "COOKIE" ? 1.38 : 1.18;
    var base = Math.floor(upgrade.baseTreatCost * Math.pow(growth, Math.max(0, owned)));
    var shopper = Math.min(5, Math.max(0, Number(state.prestigeSkills.SMART_SHOPPER) || 0));
    return Math.max(1, Math.floor(base * (100 - shopper * 5) / 100));
  }

  function ticketCost(upgrade) {
    if (upgrade.type !== "TICKET") return 0;
    var owned = Number(state.upgrades[upgrade.id]) || 0;
    var base = upgrade.baseTicketCost + Math.min(2, Math.floor(Math.max(0, owned) / 3));
    var sense = Math.min(5, Math.max(0, Number(state.prestigeSkills.TICKET_SENSE) || 0));
    var reduction = (sense >= 2 ? 1 : 0) + (sense >= 4 ? 1 : 0);
    return Math.max(1, base - reduction);
  }

  function renderShop() {
    var grid = document.getElementById("shopGrid");
    if (!grid) return;
    grid.innerHTML = "";
    UPGRADES.forEach(function (u) {
      var owned = Number(state.upgrades[u.id]) || 0;
      var cost = treatCost(u);
      var tickets = ticketCost(u);
      var ticketOwned = u.rarity ? Number(state.ticketInventory[u.rarity]) || 0 : 0;
      var canBuy = state.treats >= cost && (u.type === "COOKIE" || ticketOwned >= tickets);
      var card = document.createElement("article");
      card.className = "upgrade-card";
      var icon = document.createElement("div"); icon.className = "upgrade-icon"; icon.textContent = u.emoji;
      var h3 = document.createElement("h3"); h3.textContent = u.name;
      var p = document.createElement("p"); p.textContent = u.description;
      var metaLine = document.createElement("div"); metaLine.className = "upgrade-meta";
      var left = document.createElement("span"); left.textContent = "Owned " + owned;
      var right = document.createElement("span"); right.textContent = u.type === "TICKET" ? u.rarity + " " + ticketOwned : "Treat upgrade";
      metaLine.append(left,right);
      var button = document.createElement("button");
      button.disabled = !canBuy;
      button.textContent = "Buy · " + formatNumber(cost) + " Treats" + (u.type === "TICKET" ? " + " + tickets + " " + u.rarity : "");
      button.addEventListener("click", function () { buyUpgrade(u); });
      card.append(icon,h3,p,metaLine,button);
      grid.appendChild(card);
    });
  }

  function buyUpgrade(u) {
    var cost = treatCost(u);
    var tickets = ticketCost(u);
    if (state.treats < cost) return;
    if (u.type === "TICKET" && (Number(state.ticketInventory[u.rarity]) || 0) < tickets) return;
    state.treats -= cost;
    if (u.type === "TICKET") state.ticketInventory[u.rarity] -= tickets;
    state.upgrades[u.id] = (Number(state.upgrades[u.id]) || 0) + 1;
    var skill = u.effect === "CLICK" ? Number(state.prestigeSkills.TAP_TRAINING) || 0 : Number(state.prestigeSkills.AUTO_TRAINING) || 0;
    if (u.effect === "CLICK") state.clickPower += u.amount + skill;
    else state.autoPerSecond += u.amount + skill;
    state.totalShopPurchases += 1;
    state.happiness = Math.min(100, state.happiness + 2);
    saveLocal(true);
    toast(u.name + " upgraded.");
  }

  function rollTicket() {
    if (Math.floor(Math.random() * 100) !== 0) return;
    var roll = Math.random();
    var rarity = roll < .58 ? "COMMON" : roll < .80 ? "UNCOMMON" : roll < .94 ? "RARE" : roll < .99 ? "EPIC" : "LEGENDARY";
    state.ticketInventory[rarity] = (Number(state.ticketInventory[rarity]) || 0) + 1;
    state.lastTicketDrop = rarity;
    state.ticketDropSerial += 1;
    state.totalTicketsFound += 1;
    toast("🎟️ " + rarity + " Upgrade Ticket found!");
  }

  function tapPuppy() {
    var now = Date.now();
    state.treats += state.clickPower;
    state.lifetimeTreats += state.clickPower;
    state.totalTaps += 1;
    state.combo = now - state.lastTapMs <= 1100 ? state.combo + 1 : 1;
    state.bestCombo = Math.max(state.bestCombo, state.combo);
    state.lastTapMs = now;
    rollTicket();
    queueSave();
    pulsePuppy();
  }

  function pulsePuppy() {
    var img = document.getElementById("puppyImage");
    if (!img || !img.animate) return;
    img.animate([{transform:"scale(1)"},{transform:"scale(.94)"},{transform:"scale(1.03)"},{transform:"scale(1)"}],{duration:170,easing:"ease-out"});
  }

  function care(kind) {
    if (kind === "feed") state.fullness = Math.min(100, state.fullness + 18);
    if (kind === "play") { state.happiness = Math.min(100, state.happiness + 16); state.energy = Math.max(0, state.energy - 7); }
    if (kind === "wash") state.cleanliness = Math.min(100, state.cleanliness + 22);
    if (kind === "rest") state.energy = Math.min(100, state.energy + 24);
    state.bond = Math.min(100, state.bond + 1);
    state.careActions += 1;
    saveLocal(true);
    toast("Care complete · " + careScore() + "% wellness");
  }

  function casinoSpend(amount) {
    amount = Math.max(0, Math.floor(amount));
    if (state.treats < amount) { toast("Not enough Treats."); return false; }
    state.treats -= amount;
    return true;
  }

  function casinoPayout(amount) {
    amount = Math.max(0, Math.floor(amount));
    state.treats += amount;
    state.lifetimeTreats += amount;
  }

  function playScratcher() {
    var wager = Number(document.getElementById("scratchWager").value) || 50;
    if (!casinoSpend(wager)) return;
    var r = Math.random();
    var mult = r < .40 ? 0 : r < .64 ? .5 : r < .84 ? 1 : r < .96 ? 2 : 5;
    var payout = Math.floor(wager * mult);
    casinoPayout(payout);
    text("scratchResult", payout ? "Won " + payout + " Treats · " + mult + "×" : "No prize this card");
    saveLocal(true);
  }

  function playSlots() {
    var wager = 25;
    if (!casinoSpend(wager)) return;
    var symbols = ["🐾","🦴","🐶","🎟️","⭐"];
    var reels = [0,0,0].map(function () { return symbols[Math.floor(Math.random() * symbols.length)]; });
    text("slotReels", reels.join(" "));
    var payout = 0;
    if (reels[0] === reels[1] && reels[1] === reels[2]) payout = wager * (reels[0] === "⭐" ? 12 : 6);
    else if (reels[0] === reels[1] || reels[1] === reels[2] || reels[0] === reels[2]) payout = wager * 2;
    casinoPayout(payout);
    text("slotsResult", payout ? "Won " + payout + " Treats" : "No win");
    saveLocal(true);
  }

  function playRoulette(choice) {
    var wager = 25;
    if (!casinoSpend(wager)) return;
    var number = Math.floor(Math.random() * 37);
    var result = number === 0 ? "green" : number % 2 === 0 ? "black" : "red";
    var payout = result === choice ? wager * (result === "green" ? 14 : 2) : 0;
    casinoPayout(payout);
    text("rouletteResult", number + " · " + result.toUpperCase() + (payout ? " · +" + payout : " · lost"));
    saveLocal(true);
  }

  function exportSave() {
    var blob = new Blob([JSON.stringify(envelope(), null, 2)], {type:"application/json"});
    var url = URL.createObjectURL(blob);
    var a = document.createElement("a");
    a.href = url;
    a.download = "puppy-clicker-web-save.json";
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
  }

  function importSave(file) {
    if (!file) return;
    var reader = new FileReader();
    reader.onload = function () {
      try {
        var incoming = JSON.parse(String(reader.result || ""));
        if (incoming.format !== FORMAT || Number(incoming.schemaVersion) !== SCHEMA_VERSION || !incoming.game) throw new Error("Unsupported save");
        state = mergeGame(incoming.game);
        meta.playerId = incoming.playerId || meta.playerId;
        meta.cloudRevision = Math.max(0, Number(incoming.revision) || 0);
        meta.dirty = true;
        saveLocal(true);
        renderRoster();
        toast("Save imported.");
      } catch (error) {
        toast("Import failed: not a compatible Puppy Clicker cloud save.");
      }
    };
    reader.readAsText(file);
  }

  function cloudEndpoint() {
    return (localStorage.getItem(ENDPOINT_KEY) || "").trim();
  }

  function setCloudMessage(message) {
    text("cloudMessage", message);
  }

  function applyRemote(remote) {
    state = mergeGame(remote.game);
    meta.playerId = remote.playerId || meta.playerId;
    meta.cloudRevision = Math.max(0, Number(remote.revision) || 0);
    meta.dirty = false;
    meta.updatedAt = Date.parse(remote.updatedAt) || Date.now();
    saveLocal(false);
    renderRoster();
  }

  async function syncNow() {
    var endpoint = cloudEndpoint();
    if (!endpoint) { setCloudMessage("Cloud backend not configured."); toast("Add the cross-save API endpoint first."); return; }
    setCloudMessage("Checking cloud save…");
    try {
      var response = await fetch(endpoint, {method:"GET",credentials:"include",headers:{"Accept":"application/json"}});
      var remote = null;
      if (response.status !== 404) {
        if (!response.ok) throw new Error("GET " + response.status);
        var received = await response.json();
        remote = received.save || received;
        if (remote && remote.format !== FORMAT) throw new Error("Invalid cloud save format");
      }

      if (remote && Number(remote.revision) > meta.cloudRevision) {
        if (meta.dirty) {
          setCloudMessage("Sync conflict: this browser and cloud both changed. Export the local save before resolving.");
          toast("Cloud conflict detected.");
          return;
        }
        applyRemote(remote);
        setCloudMessage("Downloaded cloud revision " + meta.cloudRevision + ".");
        toast("Cloud save downloaded.");
        return;
      }

      if (!meta.dirty && remote && Number(remote.revision) === meta.cloudRevision) {
        setCloudMessage("Cloud save is up to date.");
        render();
        return;
      }

      var payload = {baseRevision:meta.cloudRevision,save:envelope()};
      var put = await fetch(endpoint, {
        method:"PUT",
        credentials:"include",
        headers:{"Content-Type":"application/json","Accept":"application/json"},
        body:JSON.stringify(payload)
      });
      if (put.status === 409) {
        setCloudMessage("Cloud revision changed before upload. Sync again after exporting this local save.");
        toast("Cloud conflict detected.");
        return;
      }
      if (!put.ok) throw new Error("PUT " + put.status);
      var result = await put.json();
      var accepted = result.save || result;
      if (accepted && accepted.game) applyRemote(accepted);
      else {
        meta.cloudRevision = Number(result.revision) || (meta.cloudRevision + 1);
        meta.dirty = false;
        saveLocal(false);
      }
      setCloudMessage("Synced revision " + meta.cloudRevision + ".");
      toast("Cross-save synced.");
    } catch (error) {
      console.warn("Cloud sync failed", error);
      setCloudMessage("Sync failed. Check the API endpoint, CORS, sign-in, and connection.");
      toast("Cross-save unavailable.");
    }
  }

  function navigate(name) {
    document.querySelectorAll(".view").forEach(function (view) { view.classList.toggle("active", view.getAttribute("data-view") === name); });
    document.querySelectorAll("[data-nav]").forEach(function (button) { button.classList.toggle("active", button.getAttribute("data-nav") === name); });
    if (name === "puppies") renderRoster();
    if (name === "settings") document.getElementById("cloudEndpoint").value = cloudEndpoint();
    window.scrollTo({top:0,behavior:"smooth"});
  }

  function text(id, value) {
    var el = document.getElementById(id);
    if (el) el.textContent = value;
  }

  var toastTimer = null;
  function toast(message) {
    var el = document.getElementById("toast");
    if (!el) return;
    el.textContent = message;
    el.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.classList.remove("show"); }, 2400);
  }

  function wireUi() {
    document.querySelectorAll("[data-nav]").forEach(function (button) {
      button.addEventListener("click", function () { navigate(button.getAttribute("data-nav")); });
    });
    document.getElementById("puppyButton").addEventListener("click", tapPuppy);
    document.getElementById("treatButton").addEventListener("click", tapPuppy);
    document.querySelectorAll("[data-care]").forEach(function (button) {
      button.addEventListener("click", function () { care(button.getAttribute("data-care")); });
    });
    document.getElementById("scratchButton").addEventListener("click", playScratcher);
    document.getElementById("slotsButton").addEventListener("click", playSlots);
    document.querySelectorAll("[data-roulette]").forEach(function (button) {
      button.addEventListener("click", function () { playRoulette(button.getAttribute("data-roulette")); });
    });
    document.getElementById("exportButton").addEventListener("click", exportSave);
    document.getElementById("importInput").addEventListener("change", function (event) {
      importSave(event.target.files && event.target.files[0]);
      event.target.value = "";
    });
    document.getElementById("saveEndpointButton").addEventListener("click", function () {
      var endpoint = document.getElementById("cloudEndpoint").value.trim();
      if (endpoint && endpoint.indexOf("https://") !== 0 && location.hostname !== "localhost") {
        toast("Cross-save endpoint must use HTTPS.");
        return;
      }
      localStorage.setItem(ENDPOINT_KEY, endpoint);
      setCloudMessage(endpoint ? "Endpoint saved. Sign-in is handled by the API." : "Cloud backend not configured.");
      render();
    });
    document.getElementById("cloudSyncButton").addEventListener("click", syncNow);
    document.getElementById("syncButton").addEventListener("click", function () {
      if (cloudEndpoint()) syncNow(); else navigate("settings");
    });
    document.getElementById("installButton").addEventListener("click", async function () {
      if (!deferredInstall) return;
      deferredInstall.prompt();
      await deferredInstall.userChoice;
      deferredInstall = null;
      document.getElementById("installButton").hidden = true;
    });
    window.addEventListener("beforeinstallprompt", function (event) {
      event.preventDefault();
      deferredInstall = event;
      document.getElementById("installButton").hidden = false;
    });
    window.addEventListener("beforeunload", function () { saveLocal(meta.dirty); });
  }

  function tick() {
    if (state.autoPerSecond > 0) {
      var bonus = careScore() >= 85 ? Math.floor(state.autoPerSecond * .1) : 0;
      var gain = state.autoPerSecond + bonus;
      state.treats += gain;
      state.lifetimeTreats += gain;
      meta.dirty = true;
      queueSave();
    }
  }

  loadLocal();
  wireUi();
  render();
  loadRoster();
  setInterval(tick, 1000);
  setInterval(function () { if (meta.dirty) saveLocal(true); }, 15000);

  if ("serviceWorker" in navigator) {
    window.addEventListener("load", function () {
      navigator.serviceWorker.register("./sw.js").catch(function (error) { console.warn("Service worker unavailable", error); });
    });
  }
})();