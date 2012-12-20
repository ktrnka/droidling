package HCParser;

sub new
	{
	my ($class) = @_;
	
	my $this = {};
	
	bless $this;
	return $this;
	}

sub open
	{
	my ($this, $file) = @_;
	
	open($this->{'fh'}, '<:encoding(UTF-8)', $file) or die "Failed to open $file: $!\n";
	}

sub close
	{
	my ($this) = @_;
	
	if (defined $this->{'fh'})
		{
		close $this->{'fh'};
		undef $this->{'fh'};
		}
	else
		{
		warn "Closing non-existent filehandle\n";
		}
	}
	
sub read
	{
	my ($this) = @_;
	
	my $line = readline $this->{'fh'};
	
	if (not $line)
		{
		return undef;
		}
	
	$line =~ s/[\r\n]+//;
	
	my ($source, $date, $type, $categories, $content) = split /\t/, $line;
	
	return { 'source' => $source, 
		'date' => $date,
		'type' => $type,
		'categories' => $categories,
		'text' => $content
		};
	}
	
1;
