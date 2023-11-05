const {
    StorageSharedKeyCredential,
    BlobServiceClient,
} = require("@azure/storage-blob");

const containers = [
    "haitaton-paatokset-local",
    "haitaton-hankeliitteet-local",
    "haitaton-hakemusliitteet-local",
];

const storageSharedKeyCredential = new StorageSharedKeyCredential(
    "devstoreaccount1",
    "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw=="
);

const blobServiceClient = new BlobServiceClient(
    "http://127.0.0.1:10000/devstoreaccount1",
    storageSharedKeyCredential
);

async function main() {
    // Create containers if not exist
    const containerClients = containers.map((container) => {
        const containerClient = blobServiceClient.getContainerClient(container);
        return containerClient.createIfNotExists();
    });
    await Promise.all(containerClients);
}

main()
    .then(() => console.log("Blob containers created (if not already existed)."))
    .catch((err) => console.log(`Error: ${err}`));
