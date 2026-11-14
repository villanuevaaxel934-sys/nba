import json
import os
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
import urllib.request

ESPN_TO_NBA = {
    "GS": "GSW",
    "NO": "NOP",
    "NY": "NYK",
    "SA": "SAS",
    "UTAH": "UTA",
    "WSH": "WAS",
}

HEADERS = {"User-Agent": "Mozilla/5.0"}

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(BASE_DIR, "data")
CACHE_PATH = os.path.join(DATA_DIR, "partidos_cache.json")
OUT_PATH = os.path.join(DATA_DIR, "equipos.json")


def fetch_json(url, timeout=15):
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def norm_abbr(abbr):
    return ESPN_TO_NBA.get(abbr, abbr)


def parse_attempt(val):
    # Formato "made-attempted" (ej. "47-107")
    parts = str(val).split("-")
    return float(parts[1]) if len(parts) == 2 else 0.0


def get_teams():
    data = fetch_json("https://site.api.espn.com/apis/site/v2/sports/basketball/nba/teams")
    teams = []
    for item in data["sports"][0]["leagues"][0]["teams"]:
        t = item["team"]
        teams.append((str(t["id"]), norm_abbr(t["abbreviation"])))
    return teams


def get_completed_event_ids(team_id):
    url = f"https://site.api.espn.com/apis/site/v2/sports/basketball/nba/teams/{team_id}/schedule?seasontype=2"
    data = fetch_json(url)
    ids = []
    for ev in data.get("events", []):
        comp = (ev.get("competitions") or [{}])[0]
        completed = comp.get("status", {}).get("type", {}).get("completed", False)
        if completed:
            ids.append(str(ev["id"]))
    return ids


def fetch_game_summary(event_id):
    url = f"https://site.api.espn.com/apis/site/v2/sports/basketball/nba/summary?event={event_id}"
    data = fetch_json(url, timeout=20)
    comp = data["header"]["competitions"][0]
    competitors = comp.get("competitors", [])
    box_teams = data.get("boxscore", {}).get("teams", [])
    if len(competitors) != 2 or len(box_teams) != 2:
        return None

    scores = {}
    periods = 4
    for c in competitors:
        abbr = norm_abbr(c["team"]["abbreviation"])
        scores[abbr] = float(c["score"])
        periods = max(periods, len(c.get("linescores") or []))
    mins = 48.0 + 5.0 * max(0, periods - 4)

    stats_by_team = {}
    for bt in box_teams:
        abbr = norm_abbr(bt["team"]["abbreviation"])
        stat_map = {s["name"]: s["displayValue"] for s in bt.get("statistics", [])}
        fga = parse_attempt(stat_map.get("fieldGoalsMade-fieldGoalsAttempted", "0-0"))
        fta = parse_attempt(stat_map.get("freeThrowsMade-freeThrowsAttempted", "0-0"))
        oreb = float(stat_map.get("offensiveRebounds", 0) or 0)
        tov = float(stat_map.get("totalTurnovers", stat_map.get("turnovers", 0)) or 0)
        if fga <= 0:
            return None
        poss = fga - oreb + tov + 0.44 * fta
        stats_by_team[abbr] = {"pts": scores.get(abbr, 0.0), "poss": poss}

    if len(stats_by_team) != 2:
        return None

    abbrs = list(stats_by_team.keys())
    avg_poss = (stats_by_team[abbrs[0]]["poss"] + stats_by_team[abbrs[1]]["poss"]) / 2.0

    return {
        "id": event_id,
        "date": comp.get("date", ""),
        "mins": mins,
        "poss": avg_poss,
        "teams": {
            abbrs[0]: {"pts": stats_by_team[abbrs[0]]["pts"], "opp": stats_by_team[abbrs[1]]["pts"]},
            abbrs[1]: {"pts": stats_by_team[abbrs[1]]["pts"], "opp": stats_by_team[abbrs[0]]["pts"]},
        },
    }


def calc_metrics(games, abbr):
    if not games:
        return None
    pts = sum(g["teams"][abbr]["pts"] for g in games)
    opp = sum(g["teams"][abbr]["opp"] for g in games)
    poss = sum(g["poss"] for g in games)
    mins = sum(g["mins"] for g in games)
    if poss <= 0 or mins <= 0:
        return None
    return {
        "off": round(100.0 * pts / poss, 1),
        "def": round(100.0 * opp / poss, 1),
        "pace": round(48.0 * poss / mins, 2),
    }


def main():
    os.makedirs(DATA_DIR, exist_ok=True)

    cache = {}
    if os.path.exists(CACHE_PATH):
        with open(CACHE_PATH, "r", encoding="utf-8") as f:
            cache = json.load(f)

    teams = get_teams()
    all_event_ids = set()
    with ThreadPoolExecutor(max_workers=10) as pool:
        futures = {pool.submit(get_completed_event_ids, tid): abbr for tid, abbr in teams}
        for fut in as_completed(futures):
            for eid in fut.result():
                all_event_ids.add(eid)

    missing = sorted(eid for eid in all_event_ids if eid not in cache)
    print(f"Equipos: {len(teams)} | Partidos totales: {len(all_event_ids)} | Nuevos por descargar: {len(missing)}")

    if missing:
        with ThreadPoolExecutor(max_workers=10) as pool:
            futures = {pool.submit(fetch_game_summary, eid): eid for eid in missing}
            for fut in as_completed(futures):
                eid = futures[fut]
                try:
                    game = fut.result()
                    if game:
                        cache[eid] = game
                except Exception as exc:
                    print(f"Aviso: no se pudo leer partido {eid}: {exc}")

        with open(CACHE_PATH, "w", encoding="utf-8") as f:
            json.dump(cache, f, separators=(",", ":"))

    # Agrupar partidos válidos por equipo y ordenar por fecha
    by_team = {abbr: [] for _, abbr in teams}
    for game in cache.values():
        for abbr in game.get("teams", {}):
            if abbr in by_team:
                by_team[abbr].append(game)

    equipos_out = {}
    for abbr, games in sorted(by_team.items()):
        games.sort(key=lambda g: g.get("date", ""))
        season_stats = calc_metrics(games, abbr)
        l10_stats = calc_metrics(games[-10:], abbr)
        if season_stats and l10_stats:
            equipos_out[abbr] = {
                "season": season_stats,
                "l10": l10_stats,
                "pj": len(games),
            }

    payload = {
        "actualizado": datetime.now(timezone.utc).isoformat(),
        "fuente": "ESPN",
        "equipos": equipos_out,
    }
    with open(OUT_PATH, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)

    print(f"Listo: {len(equipos_out)} equipos guardados en {OUT_PATH}")


if __name__ == "__main__":
    main()
