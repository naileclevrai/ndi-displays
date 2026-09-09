"""Posts newly approved CurseForge files to Discord.

Reads the CFWidget file list the workflow filtered (new.json), the whole project record
(project.json, for the logo), and CHANGELOG.md, and sends one embed per file: the role ping
above it, the download link first, then the version's Added / Changed / Fixed sections as
labelled blocks. Discord renders markdown inside embeds, so bullets and links survive.
"""
import json
import os
import re
import sys
import urllib.request

new_path, project_path, changelog_path = sys.argv[1:4]
webhook = os.environ["DISCORD_WEBHOOK"]
role_id = os.environ.get("ROLE_ID", "").strip()

files = json.load(open(new_path, encoding="utf-8"))
project = json.load(open(project_path, encoding="utf-8"))
changelog = open(changelog_path, encoding="utf-8").read()

KIND = {
    "release": ("New release", 0x57F287),
    "beta": ("New beta", 0xF1C40F),
    "alpha": ("New alpha", 0xED4245),
}
SECTION_LABEL = {
    "added": "✨ Added",
    "changed": "🔧 Changed",
    "fixed": "🐛 Fixed",
    "removed": "🗑️ Removed",
    "deprecated": "⚠️ Deprecated",
    "security": "🔒 Security",
}


def version_of(file_name: str) -> str:
    """ndidisplays-1.20.1-1.1.1-beta.1-all.jar -> 1.1.1-beta.1"""
    m = re.match(r"^ndidisplays-[0-9.]+-(.+?)(-all)?\.jar$", file_name)
    return m.group(1) if m else ""


def section_for(version: str) -> str:
    """The version's own block of CHANGELOG.md, without its heading."""
    m = re.search(r"^## \[" + re.escape(version) + r"\][^\n]*\n(.*?)(?=^## \[|\Z)",
                  changelog, re.S | re.M)
    return absolute_links(m.group(1).strip()) if m else ""


REPO_BLOB = "https://github.com/" + os.environ.get("GITHUB_REPOSITORY", "nanocodium/ndi-displays") + "/blob/main/"


def absolute_links(text: str) -> str:
    """Relative changelog links (docs/guide/x.md) point into the repo; Discord needs full URLs."""
    return re.sub(r"\]\((?!https?://|#)([^)]+)\)", lambda m: "](" + REPO_BLOB + m.group(1) + ")", text)


def clamp(text: str, limit: int) -> str:
    if len(text) <= limit:
        return text
    cut = text[: limit - 2].rsplit("\n", 1)[0]
    return cut + "\n…"


def fields_for(section: str):
    """Split '### Added' style subsections into embed fields; anything before them is the lead."""
    lead_lines, fields, current = [], [], None
    for line in section.splitlines():
        h = re.match(r"^###\s+(.+?)\s*$", line)
        if h:
            current = h.group(1).strip()
            fields.append([SECTION_LABEL.get(current.lower(), current), []])
            continue
        if current is None:
            lead_lines.append(line)
        else:
            fields[-1][1].append(line)
    lead = "\n".join(lead_lines).strip()
    # The release preamble ("Ship the -all jar. Includes ...") is for maintainers, not players.
    lead = "\n".join(l for l in lead.splitlines() if not l.startswith("Ship ") and "Includes [" not in l).strip()
    out = []
    for name, lines in fields:
        body = "\n".join(l.replace("- ", "• ", 1) if l.startswith("- ") else l for l in lines).strip()
        if body:
            out.append({"name": name, "value": clamp(body, 1024), "inline": False})
    return lead, out


logo = (project.get("thumbnail") or "").strip()
for f in files:
    kind, colour = KIND.get(f.get("type", ""), ("New build", 0x5865F2))
    version = version_of(f.get("name", "")) or f.get("display", "")
    page = f["url"]
    lead, fields = fields_for(section_for(version))
    versions = " · ".join(f.get("versions", [])[:6])

    description = f"**[⬇ Download {f['name']} on CurseForge]({page})**"
    if versions:
        description += f"\n{versions}"
    if lead:
        description += "\n\n" + clamp(lead, 1500)
    if not fields and not lead:
        description += "\n\nSee the file page for details."

    embed = {
        "title": f"{kind}: NDI Stage Displays {version}",
        "url": page,
        "color": colour,
        "description": description,
        "fields": fields[:6],
        "footer": {"text": "Approved on CurseForge · Minecraft 1.20.1 · Forge · needs the NDI runtime for live video"},
    }
    if logo:
        embed["thumbnail"] = {"url": logo}

    payload = {
        "content": f"<@&{role_id}>" if role_id else "",
        "embeds": [embed],
        "allowed_mentions": {"roles": [role_id]} if role_id else {"parse": []},
    }
    req = urllib.request.Request(webhook, data=json.dumps(payload).encode("utf-8"),
                                 headers={"Content-Type": "application/json", "User-Agent": "ndidisplays-announce"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        resp.read()
    print(f"announced {f.get('display')} ({f.get('id')}) as {version}")
