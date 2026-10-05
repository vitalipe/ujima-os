(ns tools.cmd.shell
  "The `shell` noun — the dev loop for desktop/shell: `sim` serves a fake desktop tier from this
   machine and points a Pi's shell at it. The work is tools.shell.sim."
  (:require [tools.shell.check :as check]
            [tools.shell.sim   :as sim]))


(def ^:private ssh-spec
  {:ip       {:desc "The dev Pi whose shell to point here" :require true :coerce :string}
   :user     {:desc "SSH user"     :default "ujima" :coerce :string}
   :password {:desc "SSH password" :default "ujima" :coerce :string}
   :port     {:desc "SSH port"     :default "22"    :coerce :string}})


(def cli
  {"shell"
   {"sim"
    {"up"
     {:usage "Usage: shell sim up <ip> [--state <name>] [--http-port 1346]"
      :target sim/up!
      :args [:ip]
      :spec (merge ssh-spec
                   {:state     {:desc "State to start in (tools/shell/states.edn)" :coerce :string}
                    :http-port {:desc "Port the fake tier listens on" :coerce :long}})}

     "down"
     {:usage "Usage: shell sim down <ip>"
      :target sim/down!
      :args [:ip]
      :spec ssh-spec}

     "check"
     {:usage "Usage: shell sim check <ip> [--renderer qt|eww] [--out out/shell-check]"
      :target check/check!
      :args [:ip]
      :spec (merge ssh-spec
                   {:renderer  {:desc "qt (the shell, + its clicks) or eww (v05's bars, visuals only)" :coerce :string}
                    :out       {:desc "Where the captures land (<out>/<renderer>/)" :coerce :string}
                    :clicks-only {:desc "Skip the captures, play only the clicks" :coerce :boolean}
                    :http-port {:desc "Port the fake tier listens on" :coerce :long}})}

     "soak"
     {:usage "Usage: shell sim soak <ip> [--cycles 1000]"
      :target check/soak!
      :args [:ip]
      :spec (merge ssh-spec {:cycles    {:desc "State changes to churn through" :coerce :long}
                             :http-port {:desc "Port the fake tier listens on" :coerce :long}})}

     "compare"
     {:usage "Usage: shell sim compare [--a out/shell-check/eww] [--b out/shell-check/qt]"
      :target check/compare!
      :spec {:a   {:desc "One run's captures" :coerce :string}
             :b   {:desc "The other's" :coerce :string}
             :out {:desc "Where compare.html lands" :coerce :string}}}}}})
