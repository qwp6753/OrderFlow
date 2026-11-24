// 仅用于开发数据库：会创建一个商品和一个零库存 SKU，不清空数据。
import assert from 'node:assert/strict';
const base=process.env.API_BASE_URL||'http://localhost:8080';
const password=process.env.E2E_PASSWORD;
if(!password)throw new Error('请设置开发账号密码 E2E_PASSWORD');
async function request(path,{method='GET',token,body,status=200}={}){
 const response=await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(body?{body:JSON.stringify(body)}:{})});
 assert.equal(response.status,status,method+' '+path);return response.json();
}
async function login(username){return (await request('/api/auth/login',{method:'POST',body:{username,password}})).data.token}
await request('/api/products',{status:401});
await request('/api/auth/login',{method:'POST',body:{username:'operator',password:'wrong-password'},status:401});
const operator=await login('operator'),warehouse=await login('warehouse');
await request('/api/admin/users',{token:operator,status:403});
await request('/api/products',{method:'POST',token:warehouse,body:{name:'权限检查',description:''},status:403});
const product=(await request('/api/products',{method:'POST',token:operator,body:{name:'API验收-'+Date.now(),description:'开发数据库烟雾验证'}})).data;
const sku={code:'API-'+Date.now(),name:'标准款',price:'19.90',status:'ON_SALE'};
await request('/api/products/'+product.id+'/skus',{method:'POST',token:operator,body:sku});
await request('/api/products/'+product.id+'/skus',{method:'POST',token:operator,body:sku,status:409});
await request('/api/products/'+product.id+'/skus',{method:'POST',token:operator,body:{...sku,code:sku.code.toLowerCase()},status:409});
const stock=(await request('/api/products/'+product.id+'/skus',{token:warehouse})).data;
assert.equal(stock.length,1);assert.equal(stock[0].availableQuantity,0);
await request('/api/products?page=1&size=101',{token:operator,status:400});
console.log('API smoke passed: authentication, role isolation, real writes, SKU uniqueness/collation, rollback and inventory query.');
