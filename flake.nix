{
  description = "d1-jdbc-driver — JDBC driver for Cloudflare D1, backed by wrangler";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs =
    { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs { inherit system; };
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          pkgs.jdk21
          pkgs.gradle
        ];
        # Gradle's JVM does not read http_proxy/https_proxy env vars; surface
        # them as JVM system properties so dependency downloads work behind a
        # proxy (no-op when there is none).
        shellHook = ''
          export JAVA_HOME="${pkgs.jdk21}"
        '';
      };
    };
}
