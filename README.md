# DotNative.InAppWebView

An advanced native browser surface for DotNative on Android, iOS and macOS. It
provides the same top-level browser controls as DotNative.WebView, plus loading
HTML with an HTTP(S) base URL and a cookie store API.

```csharp
builder.Services.AddInAppWebView();
var browser = services.InAppWebView;
await browser.OpenAsync(new Uri("https://example.com"), new InAppWebViewOptions
{
    JavaScriptEnabled = true
});
await browser.SetCookieAsync(new Uri("https://example.com"), "session", "abc");
var cookies = await browser.GetCookiesAsync(new Uri("https://example.com"));
```

JavaScript is disabled by default. Enable it only for content you trust. Android
blocks file/content access and mixed content. For Android, add
`android.permission.INTERNET` to the app manifest. App Transport Security and
Android cleartext policy still apply; HTTPS is recommended.

| Platform | Native backend |
| --- | --- |
| Android | `android.webkit.WebView` |
| iOS | `WKWebView` |
| macOS | `WKWebView` |
| Windows | Not implemented |
| Linux | Not implemented |

The browser is presented as a native top-level surface; it cannot be embedded in
a DotNative layout or clipped by a scroll view until the renderer exposes its
native-view plugin contract. The cookie API stores cookies in the system WebView
store. `GetCookiesAsync` returns a `name=value` header string. Cookie behavior can
vary by platform policy and storage lifecycle. This package does not implement a
JavaScript-to-C# message bridge, request interception, custom schemes, or file
URL access.

## Source origin

This independently authored DotNative implementation is MIT licensed.
See [source origin](ORIGIN.md).

## Service access

Import `DotNative.InAppWebView` to access the plugin through `IServiceProvider`:

```csharp
using DotNative.InAppWebView;

var plugin = services.InAppWebView;
```

The getter calls `GetRequiredService<IInAppWebView>()` on every access, preserving
DI lifetimes and the usual missing-registration error. Register the plugin with
`AddInAppWebView(...)` before building the provider.

A `net10.0` application uses the property syntax with C# 14 or later. A
`net9.0` application uses only the method equivalent:

```csharp
var plugin = services.InAppWebView();
```

The package contains separate `net9.0` and `net10.0` assemblies. NuGet selects
the assembly matching the application target framework. `NET10_0_OR_GREATER`
selects the property; the `#else` branch selects the method.

Build and pack both targets with .NET 10 SDK. A source build using .NET 9 SDK
builds only `net9.0`; it does not produce the .NET 10 assembly.
