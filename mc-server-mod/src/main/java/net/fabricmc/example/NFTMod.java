package net.fabricmc.example;

import static net.minecraft.server.command.CommandManager.literal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mojang.brigadier.context.CommandContext;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.*;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import org.bouncycastle.util.encoders.Hex;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.crypto.Hash;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;


class Response{
	public String getResult() {
		return result;
	}

	public void setResult(String result) {
		this.result = result;
	}

	public String result;
}
class Result{
	public String getInput() {
		return input;
	}

	public void setInput(String input) {
		this.input = input;
	}

	String input;
}
class PolygonResponse{
	public Result[] getResult() {
		return result;
	}

	public void setResult(Result[] result) {
		this.result = result;
	}

	Result[] result;
}


public class NFTMod implements ModInitializer {

	HashMap<Short, Block> short_to_block = new HashMap<>();
	HashMap<Block, Short> block_to_short = new HashMap<>();

	final String NFT_ADDRESS = "0xa0DA547263118039BD43E03B5d183416Dc2c1B3C";
	String API_KEY;

	final int NUM_VOXELS = (16*16*256);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.


		try {
			API_KEY = Files.readString(Path.of("key.secret"));
		} catch (IOException e) {
			System.out.println("Failed to load API key with error: " + e);
		}


		for (int i = 0; i<Math.min(Registry.BLOCK.stream().toArray().length,0xFFFF);i++){
//			System.out.printf("i: %d, block: %s%n", i,Registry.BLOCK.get(i) );
			register_block((byte)i, Registry.BLOCK.get(i));
		}
		register_block((short) 0xFFFF, null); //Unknown type, don't fill
		CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
			dispatcher.register(literal("set_state").executes(this::set_state));
			dispatcher.register(literal("grab_chunk").executes(this::grab_chunk));
			dispatcher.register(literal("get_state").executes(this::get_state));
		});

	}

	void register_block(short inserted_short, Block inserted_block){
		short_to_block.put(inserted_short, inserted_block);
		block_to_short.put(inserted_block, inserted_short);
	}
	public int grab_chunk(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {

		ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
		ChunkPos chunkPos =  serverPlayerEntity.getChunkPos();
		int chunk_x = chunkPos.x;
		int chunk_z = chunkPos.z;

		String chunk_string = "{\"data\":[";
		chunk_string += "]";

		chunk_string += String.format( ",\"chunk_x\":%d,\"chunk_z\":%d}",chunk_x,chunk_z);

		System.out.println("Finished assembling Json");

		HttpClient client = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:8000/create_transaction_url?function=grab_chunk"))
				.POST(HttpRequest.BodyPublishers.ofString(chunk_string))
				.build();
		client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(HttpResponse::body)
				.thenAccept((response)->{

					String command = "tellraw @p [\"\",\"Click here to complete transaction: \",{\"text\":\"Complete Transaction\",\"underlined\":true,\"color\":\"blue\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"http://127.0.0.1:8000/transaction?id=" + response + "\"}}]";
					CommandManager commandManager = context.getSource().getServer().getCommandManager();
					commandManager.execute(context.getSource().getServer().getCommandSource(), command);

				})
				.join();
		return 1;

	}

	public int set_state(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {

		ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();


		ChunkPos chunkPos =  serverPlayerEntity.getChunkPos();
		BlockPos startBlockPos = chunkPos.getStartPos();
		World world = serverPlayerEntity.getEntityWorld();

		System.out.println("creating array");
		short[] chunk_data = new short[16*16*256];
		int current_iteration = 0;
		for(int x = 0; x<16; x++){
			for(int z = 0; z < 16; z++){
				for(int y = 0; y < 256; y++){
					BlockPos blockPos = startBlockPos.add(x,y,z);
					BlockState blockState =  world.getBlockState(blockPos);
					if(block_to_short.get(blockState.getBlock()) != null){
						chunk_data[current_iteration] = block_to_short.get(blockState.getBlock());
//						chunk_data[x*z*y] = 5;
					}else{
						chunk_data[current_iteration] = (byte) 255;
					}
					current_iteration++;
				}
			}
		}

		System.out.println("Finished assembling block array");
		int chunk_x = chunkPos.x;
		int chunk_z = chunkPos.z;

		String chunk_string = "{\"data\":[";
		for(int i = 0; i < chunk_data.length; i++){
			chunk_string += String.format("%d", Short.toUnsignedInt(chunk_data[i]));
			if(i < chunk_data.length-1){
				chunk_string += ",";
			}
		}
		chunk_string += "]";

		chunk_string += String.format( ",\"chunk_x\":%d,\"chunk_z\":%d}",chunk_x,chunk_z);

		System.out.println("chunk_string: "+ chunk_string);

		System.out.println("Finished assembling Json");

		HttpClient client = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:8000/create_transaction_url?function=set_state"))
				.POST(HttpRequest.BodyPublishers.ofString(chunk_string))
				.build();
		client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(HttpResponse::body)
				.thenAccept((response)->{
					String command = "tellraw @p [\"\",\"Click here to complete transaction: \",{\"text\":\"Complete Transaction\",\"underlined\":true,\"color\":\"blue\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"http://127.0.0.1:8000/transaction?id=" + response + "\"}}]";
					CommandManager commandManager = context.getSource().getServer().getCommandManager();
					commandManager.execute(context.getSource().getServer().getCommandSource(), command);
				})
				.join();
		return 1;

	}

	private byte[] byte_pad(byte[] bytes_to_pad, int num_bytes){
		byte[] output = new byte[num_bytes]; //initialized as zero
		if (bytes_to_pad[0] < 0){
			Arrays.fill(output, (byte) 0xFF);
		}
//		for(int i = bytes_to_pad.length-1; i >= 0 ; i--){
//			output[i] = bytes_to_pad[i];
//		}

		System.arraycopy(bytes_to_pad, 0, output, num_bytes - bytes_to_pad.length, bytes_to_pad.length);

		return output;
	}

	private byte[] byte_concat(byte[] first_bytes, byte[] second_bytes){
		byte[] output = new byte[first_bytes.length + second_bytes.length]; //initialized as zero

		for (int i = 0; i < output.length; i++){
			if(i < first_bytes.length){
				output[i] = first_bytes[i];
			}else{
				output[i] = second_bytes[i-first_bytes.length];
			}
		}

		return output;
	}

	public int get_state(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {

		ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
		ChunkPos chunkPos =  serverPlayerEntity.getChunkPos();
		World world = serverPlayerEntity.getEntityWorld();

		byte[] chunk_x = byte_pad(ByteBuffer.allocate(4).putInt(chunkPos.x).array(), 16);

		byte[] chunk_z = byte_pad(ByteBuffer.allocate(4).putInt(chunkPos.z).array(), 16);



		byte[] token_id_bytes = byte_concat(chunk_x,chunk_z);


		System.out.print("token id: ");
		for (byte token_id_byte : token_id_bytes) {
			System.out.printf("%x", token_id_byte);
		}
		System.out.println();

		Function function = null;
		try{
			function = FunctionEncoder.makeFunction(
					"chunk_state_hash",
					List.of("uint256"),
					Arrays.asList(new Object[]{token_id_bytes}),
					List.of("bytes32")
			);
		}catch(Exception e){ //this is bad and lazy
			System.out.println("Exception when making function: " + e.getCause());
		}


		String data = FunctionEncoder.encode(function);
		System.out.println("encoding: "  + data);



		HttpClient client = HttpClient.newHttpClient();
		HttpRequest request = null;
		try {
			request = HttpRequest.newBuilder()
					.uri(URI.create(Files.readString(Path.of("api.secret"))))
					.POST(
							HttpRequest.BodyPublishers.ofString(String.format("""
									{
									  "jsonrpc": "2.0",
									  "method": "eth_call",
									  "params":
									  [
										{
										  "to": "%s",
										  "data": "%s"
										},
										"latest"
									  ],
									  "id":1
									}""",NFT_ADDRESS,data))
					)
					.build();
		} catch (IOException e) {
			e.printStackTrace();
		}
		client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(HttpResponse::body)
				.thenAccept((response)->{
					ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);

					try {
						Response mapped_response = mapper.readValue(response, Response.class);
						String blockchain_chunk_hash = mapped_response.result;

						HttpRequest polygon_request = HttpRequest.newBuilder(URI.create(String.format("https://api-testnet.polygonscan.com/api?module=account&action=txlist&address=%s&startblock=0&endblock=99999999&sort=desc&apikey=%s",NFT_ADDRESS, API_KEY))).build();

						client.sendAsync(polygon_request,HttpResponse.BodyHandlers.ofString())
								.thenApply(HttpResponse::body)
								.thenAccept((polygon_response) ->{
									try {
										PolygonResponse polygonResponse = mapper.readValue(polygon_response, PolygonResponse.class);
										if(polygonResponse.result == null){
											return;
										}
										for(int i = 0; i< polygonResponse.result.length; i++){
											if(polygonResponse.result[i].input.length() < 2+(4+32)*2){
												continue;
											}
											String chunk_data_raw = polygonResponse.result[i].input.substring(2+(4+32)*2); //0x + 4 byte function signature + first argument
											String parameter_chunk_hash =  (Hash.sha3(chunk_data_raw));
											System.out.printf("\nparameter: %s\nblockchain: %s%n", parameter_chunk_hash, blockchain_chunk_hash);
											if(parameter_chunk_hash.equals(blockchain_chunk_hash)){
												byte[] decodedBytes;
//												System.out.println(chunk_data_raw);
												decodedBytes = Hex.decode(chunk_data_raw);
												System.out.println("hit");
												System.out.println("length: " + decodedBytes.length);
												System.out.println("decodedBytes: " + Arrays.toString(decodedBytes));
												if(decodedBytes.length != NUM_VOXELS*2){
													continue;
												}
												short[] chunk_data = ByteBuffer.wrap(decodedBytes).asShortBuffer().array();
												CompletableFuture.runAsync(()-> {

													BlockPos startBlockPos = chunkPos.getStartPos();
													//TODO:
													//delete and regenerate chunk

													int current_iteration = 0;
													for (int x = 0; x < 16; x++) {
														for (int z = 0; z < 16; z++) {
															for (int y = 0; y < 256; y++) {
																short block_index = chunk_data[current_iteration];
																if(short_to_block.get(block_index) != null){
																	BlockState blockState = short_to_block.get(block_index).getDefaultState();
																	BlockPos blockPos = startBlockPos.add(x,y,z);

																	world.setBlockState(blockPos, blockState, Block.NOTIFY_ALL);

																}
																current_iteration++;
															}
														}
													}
												});
												return;
											}
										}
									} catch (JsonProcessingException e) {
										e.printStackTrace();
									}
								}).join();

					} catch (JsonProcessingException e) {
						e.printStackTrace();
					}
				})
				.join();

		return 1;

	}

}

