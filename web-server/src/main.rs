#[macro_use] extern crate rocket;
use rand::Rng;
use std::collections::HashMap;
use rocket::State;
use std::sync::atomic::Ordering;
use std::sync::Mutex;
use rocket::response::{Redirect, content};
use std::fs::{read_to_string, File};
use rocket::response::content::{Html};
use rocket::serde::json::Json;
use std::mem::size_of;
use ethereum_types::U256;
use std::fmt::{Display, Formatter};

// const NUM_BYTES: usize = (2*16 * 16 * 256);

struct SharedState{
    chunk_map: Mutex<HashMap<String, TransactionInfo>>
}
impl SharedState{
    fn new() -> Self{
        return Self{
            chunk_map: Mutex::new(HashMap::new())
        }
    }
}
#[derive(Clone, Debug)]
enum FunctionType{
    GrabChunk,
    SetState
}
impl Display for FunctionType{
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        match self{
            FunctionType::GrabChunk => write!(f,"grab_chunk"),
            FunctionType::SetState => write!(f,"set_state")
        }
    }
}

#[derive(serde::Deserialize)]
struct ChunkInput{
    chunk_x: i128,
    chunk_z: i128,
    data:Vec<u8>,
}

#[derive(Clone, Debug)]
struct TransactionInfo {
    function_type: FunctionType,
    chunk_x: i128,
    chunk_z: i128,
    data:Vec<u8>,
}

#[derive(serde::Serialize)]
struct ChunkOutput{
    token_id: ethereum_types::U256,
    data: Vec<ethereum_types::U256>,
    function_type: String,
}

#[rocket::main]
async fn main() {
    rocket::build()
        .mount("/", routes![create_transaction_url, process_transaction,transaction_json])
        .mount("/", rocket::fs::FileServer::from("static"))
        .manage(SharedState::new())
        .launch()
        .await;
}

#[post("/create_transaction_url?<function>", data = "<chunk_input>")]
fn create_transaction_url(chunk_input: Json<ChunkInput>, function: String, shared_state: &State<SharedState>) -> String{


    println!("function: {:}", function);
    let key = String::from_utf8(rand::thread_rng().sample_iter(rand::distributions::Alphanumeric).take(16).collect::<Vec<u8>>()).unwrap();
    let mut guard = shared_state.chunk_map.lock().unwrap();

    let function_type;
    if function == String::from("grab_chunk"){
        function_type = FunctionType::GrabChunk
    }
    else if function == String::from("set_state"){
        function_type = FunctionType::SetState
    }else {
        return String::from("Unknown function type")
    }

    println!("function_type: {:}", function_type);

    let transaction = TransactionInfo {
        function_type,
        chunk_x: chunk_input.chunk_x,
        chunk_z: chunk_input.chunk_z,
        data: chunk_input.data.clone(),
    };

    guard.insert(key.clone(), transaction);
    return key;
}

#[get("/transaction?<id>")]
fn process_transaction(id: Option<&str>, shared_state: &State<SharedState>)-> Html<String>{
    let safe_id = id.unwrap_or("");
    let guard = shared_state.chunk_map.lock().unwrap();


    if guard.contains_key(safe_id){

        return Html(
            format!(r#"
            <script>window.key = "{:}" </script>
            <script type="module" src="/transaction.js"></script>
            "#, safe_id)
        );
    }
    return Html("Unknown key".parse().unwrap());
}

#[get("/transaction_json?<id>")]
fn transaction_json(id: Option<&str>, shared_state: &State<SharedState>)-> Json<ChunkOutput>{
    let safe_id = id.unwrap_or("");

    let guard = shared_state.chunk_map.lock().unwrap();

    let output;
    if guard.contains_key(safe_id){
        println!("x: {:}, y: {:}",guard[safe_id].chunk_x,guard[safe_id].chunk_z );
        let token_id_bytes: Vec<u8> = [guard[safe_id].chunk_x.to_be_bytes(), guard[safe_id].chunk_z.to_be_bytes()].concat();
        println!("token_id_bytes: {:x?}",token_id_bytes);
        let token_id = ethereum_types::U256::from(token_id_bytes.as_slice());

        let data_bytes = guard[safe_id].data.clone();

        println!("token_id: {:x}", token_id);
        let mut data = vec![];
        if data_bytes.len() > 0{
            for i in (0..data_bytes.len() - 1).step_by(size_of::<U256>()){
                let data_slice = &data_bytes[i..i+(size_of::<U256>())];
                data.push(U256::from(data_slice));
            }
        }


        output = Json(ChunkOutput{
            token_id,
            data,
            function_type: guard[safe_id].function_type.to_string()
        }
        );
    }else{
        output = Json(ChunkOutput{
            token_id: ethereum_types::U256::zero(),
            data: vec![],
            function_type: "error".to_string()
        });
    }
    return output;
}