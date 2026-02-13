# Self-Hosted Overpass API (Croatia only)

## Prerequisites

- Ubuntu server with Docker installed
- ~2 GB free disk space (Croatia extract ~183 MB, indexed DB ~1-2 GB)

## Run

```bash
docker run -d \
  --name overpass-croatia \
  --restart unless-stopped \
  -e OVERPASS_MODE=init \
  -e OVERPASS_PLANET_URL=https://download.geofabrik.de/europe/croatia-latest.osm.bz2 \
  -e OVERPASS_DIFF_URL=https://download.openstreetmap.fr/replication/europe/croatia/minute/ \
  -e OVERPASS_RULES_LOAD=10 \
  -v /opt/overpass-db:/db \
  -p 8080:80 \
  wiktorn/overpass-api
```

First start takes a while (downloads + indexes Croatia data). Check progress:

```bash
docker logs -f overpass-croatia
```

Wait until you see nginx/fcgiwrap startup messages.

## Test

```bash
curl "http://localhost:8080/api/interpreter?data=[out:json];node[place=city](43.0,13.0,46.0,20.0);out%201;"
```

## Use in the app

In the app, tap the gear icon in the top bar, add a server:
- Name: `Croatia`
- URL: `http://<your-server-ip>:8080/api/interpreter`

Select it and all Overpass queries will go to your server.

## Manage

```bash
docker stop overpass-croatia
docker start overpass-croatia
docker rm -f overpass-croatia   # removes container, data stays in /opt/overpass-db
```
