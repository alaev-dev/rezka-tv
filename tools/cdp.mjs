// Выполняет JS на открытой странице WebView через DevTools-протокол и печатает результат.
// Перед запуском: adb forward tcp:9222 localabstract:webview_devtools_remote_$(adb shell pidof dev.alaev.rezkatv)
// Использование: node tools/cdp.mjs '<выражение JS>'
const expression = process.argv[2];
if (!expression) {
  console.error("usage: node tools/cdp.mjs '<js expression>'");
  process.exit(2);
}

const pages = await (await fetch("http://localhost:9222/json")).json();
const page = pages.find((p) => p.type === "page");
if (!page) throw new Error("no debuggable page; is RezkaActivity open and the port forwarded?");

const ws = new WebSocket(page.webSocketDebuggerUrl);
ws.onopen = () =>
  ws.send(JSON.stringify({
    id: 1,
    method: "Runtime.evaluate",
    params: { expression, returnByValue: true, awaitPromise: true },
  }));
ws.onmessage = (event) => {
  const msg = JSON.parse(event.data);
  if (msg.id !== 1) return;
  // Например, страница перезагрузилась, пока ждали промис
  if (msg.error) {
    console.error(`CDP error: ${msg.error.message}`);
    process.exitCode = 1;
    ws.close();
    return;
  }
  const { result, exceptionDetails } = msg.result;
  if (exceptionDetails) {
    console.error(exceptionDetails.exception?.description ?? exceptionDetails.text);
    process.exitCode = 1;
  } else {
    console.log(JSON.stringify(result.value, null, 2));
  }
  ws.close();
};
ws.onerror = (e) => {
  console.error("websocket error", e.message ?? e);
  process.exit(1);
};
