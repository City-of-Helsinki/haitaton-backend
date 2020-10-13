docker build -t haitaton-postgres .
#add your own data path and port, I use 5433 here and ~/haitaton-data
docker run -d --rm --name haitaton-postgres -e POSTGRES_PASSWORD=postgres -p 5439:5432 --volume ~/haitaton-data:/data haitaton-postgres
