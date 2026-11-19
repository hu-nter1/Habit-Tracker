package com.personal.studytracker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class Curriculum {
    static final int VERSION = 2;

    static final class Definition {
        final String name;
        final String guidance;
        final List<String> items;

        Definition(String name, String guidance, String... items) {
            this.name = name;
            this.guidance = guidance;
            this.items = Collections.unmodifiableList(Arrays.asList(items));
        }
    }

    private Curriculum() {}

    static List<Definition> all() {
        List<Definition> topics = new ArrayList<>();
        topics.add(new Definition("Linux",
                "You already know basics — push into logs, systemd, and permission edge cases.",
                "File system hierarchy (FHS)",
                "Permissions & ownership (rwx, chmod, chown, umask)",
                "SUID/SGID/sticky bit", "ACLs (getfacl/setfacl)", "Users & groups management",
                "systemd & services (systemctl, journalctl)",
                "Process management (ps, top/htop, kill, nice)",
                "Package management (apt/yum/dnf/pacman)", "Log files (/var/log, syslog, auth.log)",
                "Shell basics (variables, loops, conditionals)",
                "Networking commands (ip, ss, netstat, iptables/nftables)",
                "Cron jobs & scheduled tasks", "Disk management (fdisk, mount, df, du)",
                "SSH configuration & key-based auth", "Environment variables & PATH",
                "Text processing (grep, sed, awk, cut, find)", "Bash history & aliases",
                "Archiving & compression (tar, gzip)",
                "Security hardening basics (fail2ban, SELinux/AppArmor)"));
        topics.add(new Definition("Networking",
                "Move to timed subnetting drills next — the models are solid.",
                "OSI model (7 layers)", "TCP/IP model", "IP addressing (IPv4, IPv6 basics)",
                "Subnetting & CIDR", "DNS (records, resolution process)", "DHCP", "ARP",
                "TCP vs UDP", "Three-way handshake",
                "Common ports & protocols (HTTP/HTTPS, FTP, SSH, SMTP)",
                "Routing basics (static/dynamic)", "Switching basics (VLANs, STP)", "NAT & PAT",
                "Firewalls & ACLs", "VPNs & tunneling",
                "Wireless networking basics (WPA2/3)",
                "Troubleshooting tools (ping, traceroute, nslookup, Wireshark)",
                "Proxy servers", "Load balancers"));
        topics.add(new Definition("Scripting",
                "Bash first, pairs directly with Linux. Python later if a CTF needs it.",
                "Bash variables, loops, conditionals, functions",
                "Bash scripting for automation", "Regular expressions",
                "Python fundamentals (data types, loops, functions)",
                "Python for security (requests, sockets, parsing)", "File I/O", "Error handling",
                "Using APIs with scripts", "Version control basics (Git)",
                "Combining scripts with cron for automation"));
        topics.add(new Definition("OS (Windows-focused)",
                "Build on your own SMB signing finding instead of starting cold.",
                "NTFS file system & permissions", "Windows processes & services",
                "Windows Registry", "Active Directory basics (domains, OUs, GPOs)",
                "Windows authentication (Kerberos, NTLM)", "SMB/RPC/NetBIOS",
                "PowerShell basics", "Windows Event Logs",
                "User account control & privilege levels", "Windows networking basics",
                "Group Policy", "Built-in security features (Windows Defender)",
                "Task Scheduler"));
        topics.add(new Definition("Cloud",
                "Light pass — enough to recognize terms in job postings.",
                "What \"the cloud\" is (IaaS/PaaS/SaaS)", "Shared responsibility model",
                "AWS basics (EC2, S3, IAM)", "Azure basics (VMs, Storage, Entra ID)",
                "GCP basics (Compute Engine, Cloud Storage, IAM)",
                "Cloud networking basics (VPCs, subnets)",
                "Cloud identity & access management (IAM)", "Common cloud misconfigurations",
                "Cloud logging & monitoring basics", "Containers & virtualization basics (Docker intro)",
                "Cloud security tools overview"));
        topics.add(new Definition("CTFs",
                "picoCTF or TryHackMe CTF rooms — not HTB yet.",
                "Picking a platform (picoCTF, TryHackMe, OverTheWire)",
                "Web exploitation basics", "Cryptography challenges", "Forensics challenges",
                "Reverse engineering intro", "Binary exploitation intro", "OSINT challenges",
                "Steganography", "Writing up your own solves", "Reading others' write-ups"));
        topics.add(new Definition("Labs",
                "Treat this as graduation practice for steps 1-6 combined.",
                "Setting up a hypervisor (VirtualBox/VMware)",
                "Building a vulnerable VM lab (Metasploitable, DVWA)",
                "Network segmentation in a lab (pfSense)", "Packet capture & analysis (Wireshark)",
                "Using Kali Linux tools in a lab", "Active Directory lab setup",
                "Practicing exploitation safely", "Documenting lab findings",
                "Snapshot & revert practice"));
        topics.add(new Definition("Cybersecurity Frameworks",
                "Recognize names and purposes, don't memorize.",
                "NIST Cybersecurity Framework (Identify, Protect, Detect, Respond, Recover)",
                "NIST 800-53 / 800-171 overview", "MITRE ATT&CK framework",
                "CIS Controls (v8)", "ISO 27001 overview", "PCI DSS overview", "OWASP Top 10",
                "Cyber Kill Chain", "Zero Trust Architecture basics",
                "Risk Management Framework (RMF) basics"));
        topics.add(new Definition("Choose a Domain",
                "Write down your reasoning — you'll want it for interviews.",
                "Research SOC Analyst role", "Research Network Security/Defense role",
                "Research Vulnerability Management role", "Research GRC (Governance, Risk, Compliance) role",
                "Research Incident Response role", "Research Penetration Testing role",
                "Research Cloud Security role", "Compare salary & demand per domain",
                "Talk to professionals in 1-2 domains", "Decide and document your reasoning"));
        topics.add(new Definition("Tools for That Domain",
                "Note which tools you actually enjoyed using.",
                "Identify top tools mentioned in job postings for your domain",
                "SIEM tools (Splunk, Wazuh, ELK)",
                "Vulnerability scanners (Nessus, OpenVAS, Qualys)",
                "Packet analysis tools (Wireshark, tcpdump)",
                "EDR/antivirus tools (CrowdStrike, Defender)",
                "Ticketing/SOAR tools overview", "Scripting tools specific to the domain",
                "Hands-on install & configuration practice",
                "Document what you tried and preferred"));
        topics.add(new Definition("Free Certifications for That Domain",
                "Free only, for now.",
                "ISC2 Certified in Cybersecurity (CC)", "Google Cybersecurity Certificate",
                "Cisco Introduction to Cybersecurity", "Microsoft SC-900 (free learning path)",
                "AWS Cloud Practitioner Essentials (free training)",
                "IBM Cybersecurity free courses", "Research domain-specific free certs",
                "Schedule and complete your chosen cert"));
        topics.add(new Definition("Post Achievements on LinkedIn",
                "Build your public trail before applying.",
                "Set up/optimize your LinkedIn profile",
                "Write a post for each certificate earned",
                "Write a post for each CTF/lab completed",
                "Include screenshots or write-up links",
                "Engage with cybersecurity community posts",
                "Connect with professionals in your chosen domain",
                "Set a consistency schedule (e.g. weekly)"));
        topics.add(new Definition("Apply for Jobs",
                "By now, every claim on your CV has proof behind it.",
                "Polish CV with projects & certs included", "Optimize LinkedIn headline & about section",
                "Apply weekly (set a target number)", "Tailor CV per application",
                "Prepare for technical interviews", "Prepare for behavioral interviews",
                "Follow up on applications", "Track applications in a spreadsheet"));
        return Collections.unmodifiableList(topics);
    }
}
