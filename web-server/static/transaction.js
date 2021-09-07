import {ethers} from  "./ethers-5.1.esm.min.js";
const NFTAddress = "0xa0DA547263118039BD43E03B5d183416Dc2c1B3C";
const NFTAbi = await(await fetch("/NFTAbi.json")).json();

await window.ethereum.request({method:'eth_requestAccounts'});

const signer = (new ethers.providers.Web3Provider(window.ethereum)).getSigner()

let contract = new ethers.Contract(NFTAddress, NFTAbi, signer);


const chunk_json = await(await fetch("/transaction_json?id=" + window.key)).json();


let data = [];

console.log(chunk_json);

for(let i = 0; i < chunk_json.data.length; i++){
    data.push(ethers.BigNumber.from(chunk_json.data[i]));
}

if(chunk_json.function_type === "grab_chunk"){
    contract.grab_chunk(ethers.BigNumber.from(chunk_json.token_id));
}else if(chunk_json.function_type === "set_state"){
    contract.set_state(ethers.BigNumber.from(chunk_json.token_id), chunk_json.data);
}else{
    alert("unknown function type");
}


