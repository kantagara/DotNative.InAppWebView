using DotNative.Plugins;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;

namespace DotNative.InAppWebView;

public interface IInAppWebView
{
    Task OpenAsync(
        Uri uri,
        InAppWebViewOptions? options = null,
        CancellationToken cancellationToken = default
    );
    Task<bool> GoBackAsync(CancellationToken cancellationToken = default);
    Task<bool> GoForwardAsync(CancellationToken cancellationToken = default);
    Task ReloadAsync(CancellationToken cancellationToken = default);
    Task StopLoadingAsync(CancellationToken cancellationToken = default);
    Task CloseAsync(CancellationToken cancellationToken = default);
    Task<string?> GetCurrentUrlAsync(CancellationToken cancellationToken = default);
    Task<string?> EvaluateJavaScriptAsync(
        string script,
        CancellationToken cancellationToken = default
    );
    Task LoadHtmlAsync(string html, Uri baseUri, CancellationToken cancellationToken = default);
    Task SetCookieAsync(
        Uri uri,
        string name,
        string value,
        CancellationToken cancellationToken = default
    );
    Task<string> GetCookiesAsync(Uri uri, CancellationToken cancellationToken = default);
    Task ClearCookiesAsync(CancellationToken cancellationToken = default);
}

public sealed record InAppWebViewOptions
{
    public bool JavaScriptEnabled { get; init; }

    public string? UserAgent { get; init; }

    public IReadOnlyDictionary<string, string>? Headers { get; init; }
}

public static class InAppWebViewServices
{
    public static IServiceCollection AddInAppWebView(this IServiceCollection services)
    {
        services.TryAddSingleton<IInAppWebView, ChannelInAppWebView>();
        return services;
    }
}

internal sealed class ChannelInAppWebView(IPlatformChannels channels) : IInAppWebView
{
    private MethodChannel Channel => channels.Get("dotnative.inapp-webview");

    public Task OpenAsync(
        Uri uri,
        InAppWebViewOptions? options = null,
        CancellationToken cancellationToken = default
    )
    {
        ValidateUri(uri);
        options ??= new();
        var args = new Dictionary<string, object?>
        {
            ["url"] = uri.AbsoluteUri,
            ["javaScriptEnabled"] = options.JavaScriptEnabled,
            ["userAgent"] = options.UserAgent,
            ["headers"] = options.Headers is null
                ? null
                : options.Headers.ToDictionary(p => p.Key, p => (object?)p.Value),
        };
        return Channel.InvokeAsync("open", args, cancellationToken);
    }

    public Task<bool> GoBackAsync(CancellationToken cancellationToken = default) =>
        Bool("goBack", cancellationToken);

    public Task<bool> GoForwardAsync(CancellationToken cancellationToken = default) =>
        Bool("goForward", cancellationToken);

    public Task ReloadAsync(CancellationToken cancellationToken = default) =>
        Call("reload", cancellationToken);

    public Task StopLoadingAsync(CancellationToken cancellationToken = default) =>
        Call("stop", cancellationToken);

    public Task CloseAsync(CancellationToken cancellationToken = default) =>
        Call("close", cancellationToken);

    public Task<string?> GetCurrentUrlAsync(CancellationToken cancellationToken = default) =>
        String("getCurrentUrl", null, cancellationToken);

    public Task<string?> EvaluateJavaScriptAsync(
        string script,
        CancellationToken cancellationToken = default
    )
    {
        ArgumentNullException.ThrowIfNull(script);
        return String(
            "evaluateJavaScript",
            new Dictionary<string, object?> { ["script"] = script },
            cancellationToken
        );
    }

    public Task LoadHtmlAsync(
        string html,
        Uri baseUri,
        CancellationToken cancellationToken = default
    )
    {
        ArgumentNullException.ThrowIfNull(html);
        ValidateUri(baseUri);
        return Call(
            "loadHtml",
            new Dictionary<string, object?> { ["html"] = html, ["baseUrl"] = baseUri.AbsoluteUri },
            cancellationToken
        );
    }

    public Task SetCookieAsync(
        Uri uri,
        string name,
        string value,
        CancellationToken cancellationToken = default
    )
    {
        ValidateUri(uri);
        ArgumentException.ThrowIfNullOrWhiteSpace(name);
        ArgumentNullException.ThrowIfNull(value);
        if (
            name.Any(c => !(char.IsAsciiLetterOrDigit(c) || c is '-' or '_'))
            || value.Contains('\r')
            || value.Contains('\n')
        )
            throw new ArgumentException("Cookie name or value contains an invalid character.");
        return Call(
            "setCookie",
            new Dictionary<string, object?>
            {
                ["url"] = uri.AbsoluteUri,
                ["name"] = name,
                ["value"] = value,
            },
            cancellationToken
        );
    }

    public async Task<string> GetCookiesAsync(
        Uri uri,
        CancellationToken cancellationToken = default
    )
    {
        ValidateUri(uri);
        return await String(
                    "getCookies",
                    new Dictionary<string, object?> { ["url"] = uri.AbsoluteUri },
                    cancellationToken
                )
                .ConfigureAwait(false)
            ?? "";
    }

    public Task ClearCookiesAsync(CancellationToken cancellationToken = default) =>
        Call("clearCookies", cancellationToken);

    private Task Call(string method, CancellationToken token) =>
        Channel.InvokeAsync(method, cancellationToken: token);

    private Task Call(string method, object args, CancellationToken token) =>
        Channel.InvokeAsync(method, args, token);

    private async Task<bool> Bool(string method, CancellationToken token)
    {
        var result = await Channel
            .InvokeAsync(method, cancellationToken: token)
            .ConfigureAwait(false);
        return result is bool value
            ? value
            : throw new InvalidDataException($"Invalid {method} response.");
    }

    private async Task<string?> String(string method, object? args, CancellationToken token)
    {
        var result = await Channel.InvokeAsync(method, args, token).ConfigureAwait(false);
        return result is null or string
            ? (string?)result
            : throw new InvalidDataException($"Invalid {method} response.");
    }

    private static void ValidateUri(Uri uri)
    {
        ArgumentNullException.ThrowIfNull(uri);
        if (
            !uri.IsAbsoluteUri
            || uri.Scheme is not ("http" or "https")
            || string.IsNullOrWhiteSpace(uri.Host)
        )
            throw new ArgumentException("An absolute HTTP or HTTPS URL is required.", nameof(uri));
    }
}
