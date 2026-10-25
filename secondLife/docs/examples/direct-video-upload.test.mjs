import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
const source=fs.readFileSync(new URL("./direct-video-upload.js",import.meta.url),"utf8")+"\n//# sourceURL=direct-video-upload.js";
const {uploadEvidenceVideo}=await import("data:text/javascript;base64,"+Buffer.from(source).toString("base64"));
const asset={public_id:"test-id",secure_url:"https://res.cloudinary.com/test.mp4",bytes:9,format:"mp4",duration:1};
async function run(finalBody,status=200) {
  const original=globalThis.fetch;
  let completes=0;
  const session={uploadId:"test-id",publicId:"test-id",uploadUrl:"https://api.cloudinary.com/test/video/upload",apiKey:"public-key",signature:"signature",parameters:{type:"authenticated"},expiresAt:new Date(Date.now()+60000).toISOString(),maxBytes:100,chunkSizeBytes:6};
  globalThis.fetch=async(url,options)=>{
    if(url.endsWith("upload-signature")) return Response.json({success:true,data:session});
    if(url.endsWith("/complete")) { completes++; return Response.json({success:true,data:{evidenceUrl:"/api/v1/reports/evidence/test-id"}}); }
    assert.equal(options.headers.Authorization,undefined);
    assert.equal(options.headers["X-Unique-Upload-Id"],"test-id");
    if(options.headers["Content-Range"]==="bytes 0-5/9") return Response.json({done:false});
    assert.equal(options.headers["Content-Range"],"bytes 6-8/9");
    return Response.json(finalBody,{status});
  };
  try {
    const result=await uploadEvidenceVideo({file:new File([new Uint8Array(9)],"proof.mp4",{type:"video/mp4"}),token:"jwt"});
    assert.equal(completes,1);
    return result;
  } catch(error) { assert.equal(completes,0); throw error; }
  finally { globalThis.fetch=original; }
}
test("accepts final asset with done true",async()=>{await run({...asset,done:true});});
test("accepts full final asset without done flag",async()=>{await run(asset);});
test("rejects explicitly incomplete response",async()=>{await assert.rejects(run({...asset,done:false}));});
test("rejects missing asset metadata",async()=>{await assert.rejects(run({done:true}));});
test("rejects a different public ID",async()=>{await assert.rejects(run({...asset,public_id:"other-id",done:true}));});
test("rejects an old asset with a different byte size",async()=>{await assert.rejects(run({...asset,bytes:8,done:true}));});
test("surfaces provider file-size rejection without completion",async()=>{
  await assert.rejects(run({error:{message:"File size too large. Maximum is 104857600."}},400),/File size too large/);
});
